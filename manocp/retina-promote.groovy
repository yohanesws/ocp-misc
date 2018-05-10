try {
    timeout(time:20 , unit: 'MINUTES'){
        def projectDev="retina-dev"
        def projectTesting="retina-testing"
        def apps=['user-interface','masterdata','report-monitoring','staging-service','new-notif','adapter','auditlog','sender','duplicate','simulator','housekeeping']
        //def apps=[]
        node ("maven"){
            stage ("promote to Testing"){
                sh "oc get svc -o jsonpath='{.items.*.metadata.name}' -n ${projectDev} > svc"
                services = readFile('svc')
                services = services.replaceAll("redis\\s","")
                //apps=services.split(" ")
                services = 'ALL\n' + services
                services = services.replaceAll("\\s","\n")
                timeout (time:10, unit: 'MINUTES'){
                    env.APP= input message : "promote to Testing", ok:"Promote",
                                                parameters: [choice(name:'APP', choices:services, description: 'Name App to Promote')]
                }
                if (env.APP != 'ALL'){
                    apps=[env.APP]
                }
                for (image in apps){
                    sh "oc export cm/${image}-cm -n ${projectDev} | oc apply -n ${projectTesting} -f -|| true"
                }
            }
            stage ("move image to Testing"){
                for (image in apps){
                    sh "oc tag ${projectDev}/${image}:latest ${projectTesting}/${image}:latest"
                }
            }
            stage ("deploy to Testing"){
                timeout (time:60, unit: 'MINUTES'){
                    input message : "deploy to Testing", ok:"Deploy"
                }
                for (image in apps){
                    sh "oc delete dc,svc,route -l app=${image} -n ${projectTesting}"
                    sh "oc new-app ${image} -n ${projectTesting}"
                    sh "oc rollout pause dc/${image} -n ${projectTesting}"
                    sh "oc set env dc/${image} TZ=Asia/Jakarta -n ${projectTesting}"
                    sh "oc set volume dc/${image} --add --name=config-volume -t configmap --configmap-name=${image}-cm --mount-path=/deployments/application.properties --overwrite -n ${projectTesting}"
                    sh "oc patch dc/${image} -p '[{\"op\":\"add\",\"path\":\"/spec/template/spec/containers/0/volumeMounts/0/subPath\",\"value\":\"application.properties\"}]' --type=json -n ${projectTesting}"
                    sh "oc rollout resume dc/${image} -n ${projectTesting}"
                }
            }
            stage ("expose apps Testing"){
                sh "oc expose svc/user-interface -n ${projectTesting} || true"
                sh "oc expose svc/masterdata -n ${projectTesting} || true"
                sh "oc expose svc/report-monitoring -n ${projectTesting} || true"
            }
             
        }
    }
}catch(err){
    echo "in catch block"
    echo "Caught : ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}