try {
    timeout(time:60 , unit: 'MINUTES'){
        def cicd="devops"
        def projectStaging="retina-staging"
        def projectProd="retina-prod"
        def ocpProd="ocpmaster.supporting.corp.bankmandiri.co.id:8443"
        def ocpNonProd="ocpmaster.supporting.devmandiri.co.id:8443"
        def nonProdRegistry="docker-registry-default.ocpdev.supporting.devmandiri.co.id"
        def apps=['user-interface','masterdata','report-monitoring','staging-service','new-notif','adapter','auditlog','sender','duplicate','simulator','housekeeping']
        node ("nonprod"){
            stage ("promote to Production"){
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'nonprod-ocp', usernameVariable: 'OCP_USERNAME', passwordVariable:"OCP_TOKEN"]]){
                    sh "oc login ${ocpNonProd} --token=${OCP_TOKEN} --insecure-skip-tls-verify=true"
                    sh "oc project ${projectStaging}"
                    sh "oc get svc -o jsonpath='{.items.*.metadata.name}' -n ${projectStaging} > svc"
                    services = readFile('svc')
                    services = services.replaceAll("redis\\s","")
                    services = 'ALL\n' + services
                    services = services.replaceAll("\\s","\n")
                    timeout (time:10, unit: 'MINUTES'){
                        env.APP= input message : "Promote to Production", ok:"Promote",
                                                    parameters: [choice(name:'APP', choices:services, description: 'Name App to Promote')]
                    }
                    if (env.APP != 'ALL'){
                        sh "oc export cm/${env.APP}-cm > cm-staging.yml"
                        apps=[env.APP]
                    }else{
                        sh "oc export cm > cm-staging.yml"
                    }
                }
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'prod-ocp', usernameVariable: 'OCP_USERNAME', passwordVariable:"OCP_TOKEN"]]){
                    sh "oc login ${ocpProd} --token=${OCP_TOKEN} --insecure-skip-tls-verify=true"
                    sh "oc project ${projectProd}"
                    sh "oc create -f cm-staging.yml || true"
                }
            }
        }
        node (){
            stage ("move image to Production"){
                sh "oc whoami"
                sh "oc project ${cicd}"
                for (image in apps){
                    sh "oc delete bc image-mover-${image} || true"
                    sh "oc tag ${cicd}/${projectStaging}-${image}:latest -d || true"
                    sh "oc delete is ${projectStaging}-${image} || true"
                    sh "oc new-build --strategy=docker --dockerfile=\"FROM ${nonProdRegistry}/${projectStaging}/${image}:latest\" --to=\"docker-registry.default.svc:5000/${cicd}/${projectStaging}-${image}:latest\" --name=image-mover-${image} --allow-missing-images"
                    sh "oc cancel-build bc/image-mover-${image}"
                    sh "oc patch bc/image-mover-${image} -p '{ \"spec\" : { \"strategy\" : { \"dockerStrategy\" : { \"forcePull\" : true}}}}'"
                    sh "oc set build-secret --pull bc/image-mover-${image} non-prod-registry"
                    sh "oc start-build bc/image-mover-${image} --wait"
                }
            }
            stage ("deploy to Prod"){
                timeout (time:60, unit: 'MINUTES'){
                    input message : "Deploy to Production", ok:"Deploy"
                }
                for (image in apps){
                    sh "oc get dc ${image} -o jsonpath='{.metadata.name}' -n ${projectProd} > dc"
                    dc = readFile('dc').trim()
                    echo 'dc ${dc}'
                    sh "oc tag ${cicd}/${projectStaging}-${image}:latest ${projectProd}/${image}:latest"
                    if (dc.contains('Error')){
                        //sh "oc delete dc,svc,route -l app=${image} -n ${projectProd}"
                        sh "oc new-app ${image} -n ${projectProd}"
                        sh "oc rollout pause dc/${image} -n ${projectProd}"
                        sh "oc set env dc/${image} TZ=Asia/Jakarta -n ${projectProd}"
                        sh "oc set volume dc/${image} --add --name=config-volume -t configmap --configmap-name=${image}-cm --mount-path=/deployments/application.properties --overwrite -n ${projectProd}"
                        sh "oc patch dc/${image} -p '[{\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/volumeMounts/0/subPath\",\"value\":\"application.properties\"}]' --type=json -n ${projectProd}"
                        sh "oc rollout resume dc/${image} -n ${projectProd}"
                        sh "oc rollout status dc/${image} -n ${projectProd}"
                    }else {
                        echo 'just update image'
                    }
                }
            }
            stage ("expose apps Prod"){
                sh "oc create route edge --service=user-interface --port=8080 --insecure-policy=Redirect -n ${projectProd} || true"
                sh "oc create route edge --service=masterdata --port=8080 --insecure-policy=Redirect  -n ${projectProd} || true"
                sh "oc create route edge --service=simulator --port=8080 --insecure-policy=Redirect -n ${projectProd} || true"
            }
             
        }
    }
}catch(err){
    echo "in catch block"
    echo "Caught : ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}