try {
    timeout(time: 20, unit: 'MINUTES') {
    def appName="myprestasi"
    def projectDev="myprestasi-dev"
    def projectSit="myprestasi-sit"
    def projectUat="myprestasi-uat"
    def projectProd="myprestasi-prod"
    def projectCicd="cicd"
    def sourceSecret="mampugit"
    def tag="blue"
    def altTag="green"
    def routeSuffix="apps.osdec.gov.my"
    def databaseSvc="mysql"
    def gitRepo="git.osdec.gov.my/spad/myprestasi.git"
    def credentialsId="mampu-git"
    def version

    properties([
        pipelineTriggers([
            pollSCM('H/10 * * * *')
        ])
    ])

    node () {
        stage ("Prepare Build"){
            git branch: "master", url: "https://"+gitRepo , credentialsId: credentialsId, poll: true, changelog: true
        }
        stage("Deploy Dev") {
            sh "oc project ${projectDev}"
            sh "oc new-app php-70-redis~${gitRepo} --name=${appName} --source-secret=${sourceSecret} || true"
            sh "oc set volume dc/${appName} -n ${projectDev} --add --name=pictures -t pvc --claim-name=myprestasi-pict-sitdev --mount-path=/opt/app-root/src/uploads || true"
            sh "oc start-build ${appName} --wait"
        }
        stage("Promote SIT") {
            timeout(time:4, unit:'HOURS') {
                env.VERSION = input message: 'Promote to SIT?', ok: 'Promote!',
                                parameters: [string(name: 'VERSION', defaultValue: '', description: 'VERSION')]
            } 
            git branch: "master", url: "https://"+gitRepo , credentialsId: credentialsId
            sh "oc tag ${projectDev}/${appName}:latest ${projectDev}/${appName}:${env.VERSION}"
            sh "oc tag ${projectDev}/${appName}:${env.VERSION} ${projectSit}/${appName}:sit-${env.VERSION}"
            sh("git config user.name 'Jenkins'")
            sh("git config user.email 'jenkins@mycompany.com'")
            sh "git tag -a ${env.VERSION}-ocp -m 'Jenkins' ||true"
            withCredentials([[$class: 'UsernamePasswordMultiBinding', 
                credentialsId: 'mampu-git', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD']]) {   
                    sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${gitRepo} --tags || true"
            }
        }
        stage("Migrate DB SIT") {
            sh "oc get pod -o jsonpath='{.items[*].metadata.name}' -l deploymentconfig=${databaseSvc} -n ${projectSit} > mysqlpod"
            mysqlpod = readFile('mysqlpod').trim()
            sh "oc exec -n ${projectSit} ${mysqlpod} -- bash -c 'mysqldump -uroot -p7cjD6vqf2QurcVHV -hmysql.myprestasi-dev.svc myprestasi|mysql -umyprestasi -pqFTdxArxPkgfe6HC myprestasi'"
        }
        stage("Deploy SIT") {
            // clean up. keep the image stream
            sh "oc export cm -n ${projectDev} | oc create -n ${projectSit} -f - || true"
            sh "oc delete dc,svc,route -l app=${appName} -n ${projectSit}"
            sh "oc new-app ${appName}:sit-${env.VERSION} -n ${projectSit}"
            sh "oc rollout cancel dc/${appName} -n ${projectSit} || true"
            sh "oc rollout pause dc/${appName} -n ${projectSit}"
            sh "oc set env  dc/${appName} -n ${projectSit} SESSION_HANDLER=redis"
            sh "oc set env  dc/${appName} -n ${projectSit} SESSION_PATH='tcp://redis:6379?auth=rAMEK4YeRoetW0Od'"
            sh "oc set volume dc/${appName} -n ${projectSit} --add --name=pictures -t pvc --claim-name=myprestasi-pict-sitdev --mount-path=/opt/app-root/src/uploads"
            sh "oc set volume dc/${appName} -n ${projectSit} --add --name=htaccess -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/.htaccess --sub-path=.htaccess"
            sh "oc set volume dc/${appName} -n ${projectSit} --add --name=database-config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/database.php --sub-path=database.php"
            sh "oc set volume dc/${appName} -n ${projectSit} --add --name=config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/config.php --sub-path=config.php"      
            sh "oc set volume dc/${appName} -n ${projectSit} --add --name=redis -t configmap --configmap-name=myprestasi-cm --mount-path=/etc/opt/rh/rh-php70/php.d/20-redis.ini --sub-path=20-redis.ini"
            sh "oc rollout resume dc/${appName} -n ${projectSit}"
            sh "oc rollout status dc/${appName} -w -n ${projectSit}"
            sh "oc expose svc/${appName} -n ${projectSit} --hostname=${appName}-sit.${routeSuffix}"
        }
        stage ('Promote UAT') {
            timeout(time:4, unit:'HOURS') {
                    env.VERSION = input message: 'Promote to UAT?', ok: 'Promote!',
                                parameters: [string(name: 'VERSION', defaultValue: '', description: 'VERSION')]
            }
            sh "oc tag ${projectSit}/${appName}:sit-${env.VERSION} ${projectUat}/${appName}:uat-${env.VERSION}"
        }
        stage("Deploy UAT") {
            sh "oc export cm -n ${projectSit} | oc create -n ${projectUat} -f - || true"
            sh "oc delete dc,svc,route -l app=${appName} -n ${projectUat}"
            sh "oc new-app ${appName}:uat-${env.VERSION} -n ${projectUat}"
            sh "oc rollout cancel dc/${appName} -n ${projectUat} || true"
            sh "oc rollout pause dc/${appName} -n ${projectUat}"
            sh "oc set env  dc/${appName} -n ${projectUat} SESSION_HANDLER=redis"
            sh "oc set env  dc/${appName} -n ${projectUat} SESSION_PATH='tcp://redis:6379?auth=Q4dxyf1VegkDDHdC'"
            sh "oc set volume dc/${appName} -n ${projectUat} --add --name=pictures -t pvc --claim-name=myprestasi-pict-uat --mount-path=/opt/app-root/src/uploads"
            sh "oc set volume dc/${appName} -n ${projectUat} --add --name=htaccess -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/.htaccess --sub-path=.htaccess"
            sh "oc set volume dc/${appName} -n ${projectUat} --add --name=database-config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/database.php --sub-path=database.php"
            sh "oc set volume dc/${appName} -n ${projectUat} --add --name=config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/config.php --sub-path=config.php"       
            sh "oc set volume dc/${appName} -n ${projectUat} --add --name=redis -t configmap --configmap-name=myprestasi-cm --mount-path=/etc/opt/rh/rh-php70/php.d/20-redis.ini --sub-path=20-redis.ini"   
            sh "oc rollout resume dc/${appName} -n ${projectUat}"
            sh "oc rollout status dc/${appName} -w -n ${projectUat}"
            sh "oc expose svc/${appName} -n ${projectUat} --hostname=${appName}-uat.${routeSuffix}"
        }
        stage ('Promote Prod') {
                timeout(time:4, unit:'HOURS') {
                    env.VERSION = input message: 'Promote to PROD?', ok: 'Promote!',
                                parameters: [string(name: 'VERSION', defaultValue: '', description: 'VERSION')]
            }
            // tag for stage
            sh "oc tag ${projectUat}/${appName}:uat-${env.VERSION} ${projectProd}/${appName}:prod-${env.VERSION}"
            
        }
        stage ('Preparing Deploy') {
            timeout(time:4, unit:'HOURS') {
                input message: "Deploy to Prod?", ok: "Deploy"
            }
            sh "oc project ${projectProd}"
            sh "oc export cm -n ${projectUat} | oc create -n ${projectProd} -f - || true"
            sh "oc get is ${appName} -n ${projectProd} -o jsonpath='{.status.tags[*].tag}' --loglevel=4 > imagetag"
            imagetag = readFile('imagetag').trim()
            echo "find image tage ${imagetag}"
            if (!imagetag.contains(tag)){
                sh "oc tag ${projectProd}/${appName}:prod-${env.VERSION} ${projectProd}/${appName}:${tag}"
                sh "oc new-app ${appName}:${tag} --name=${appName}-${tag} -n ${projectProd} "
                sh "oc rollout cancel dc/${appName}-${tag} -n ${projectProd} || true"
                sh "oc rollout pause dc/${appName}-${tag} -n ${projectProd}"
                sh "oc set env  dc/${appName}-${tag} -n ${projectProd} SESSION_HANDLER=redis"
                sh "oc set env  dc/${appName}-${tag} -n ${projectProd} SESSION_PATH='tcp://redis:6379?auth=FymbtHX7Ud6IlATr'"  
                sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=redis -t configmap --configmap-name=myprestasi-cm --mount-path=/etc/opt/rh/rh-php70/php.d/20-redis.ini --sub-path=20-redis.ini"   
                sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=pictures -t pvc --claim-name=myprestasi-pict --mount-path=/opt/app-root/src/uploads"
                sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=htaccess -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/.htaccess --sub-path=.htaccess"
                sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=database-config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/database.php --sub-path=database.php"
                sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/config.php --sub-path=config.php"      
                sh "oc rollout resume dc/${appName}-${tag} -n ${projectProd}"
                sh "oc rollout status dc/${appName}-${tag} -w -n ${projectProd}"
                sh "oc expose svc/${appName}-${tag} --hostname=${appName}-${tag}.${routeSuffix} -n ${projectProd}"
                sh "oc expose svc/${appName}-${tag} --hostname=${appName}.${routeSuffix} --name=${appName} -n ${projectProd}"
                sh "oc set -n ${projectProd} route-backends ${appName} ${appName}-${tag}=100 --loglevel=4"
            }
            if (!imagetag.contains(altTag)){
                sh "oc tag ${projectProd}/${appName}:prod-${env.VERSION} ${projectProd}/${appName}:${altTag}"
                sh "oc new-app ${appName}:${altTag} --name=${appName}-${altTag} -n ${projectProd}"
                sh "oc rollout cancel dc/${appName}-${altTag} -n ${projectProd} || true"
                sh "oc rollout pause dc/${appName}-${altTag} -n ${projectProd}"
                sh "oc set env  dc/${appName}-${altTag} -n ${projectProd} SESSION_HANDLER=redis"
                sh "oc set env  dc/${appName}-${altTag} -n ${projectProd} SESSION_PATH='tcp://redis:6379?auth=FymbtHX7Ud6IlATr'"  
                sh "oc set volume dc/${appName}-${altTag} -n ${projectProd} --add --name=redis -t configmap --configmap-name=myprestasi-cm --mount-path=/etc/opt/rh/rh-php70/php.d/20-redis.ini --sub-path=20-redis.ini"
                sh "oc set volume dc/${appName}-${altTag} -n ${projectProd} --add --name=pictures -t pvc --claim-name=myprestasi-pict --mount-path=/opt/app-root/src/uploads"
                sh "oc set volume dc/${appName}-${altTag} -n ${projectProd} --add --name=htaccess -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/.htaccess --sub-path=.htaccess"
                sh "oc set volume dc/${appName}-${altTag} -n ${projectProd} --add --name=database-config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/database.php --sub-path=database.php"
                sh "oc set volume dc/${appName}-${altTag} -n ${projectProd} --add --name=config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/config.php --sub-path=config.php"      
                sh "oc rollout resume dc/${appName}-${altTag} -n ${projectProd}"
                sh "oc rollout status dc/${appName}-${altTag} -w -n ${projectProd}"
                sh "oc expose svc/${appName}-${altTag}  --hostname=${appName}-${altTag}.${routeSuffix} -n ${projectProd}"
                sh "oc delete route ${appName} -n ${projectProd} || true"
                sh "oc expose svc/${appName}-${tag} --hostname=${appName}.${routeSuffix} --name=${appName} -n ${projectProd}"
                sh "oc patch route ${appName} -p '{\"spec\":{\"alternateBackends\":[{\"name\":\"${appName}-${altTag}\",\"weight\":0}]}}'"
            }
        }
        stage('Initialize') {
            sh "oc get route ${appName} -n ${projectProd} -o jsonpath='{ .spec.to.name }' --loglevel=4 > activeservice"
            activeService = readFile('activeservice').trim()
            if (activeService == "${appName}-blue") {
            tag = "green"
            altTag = "blue"
            }
            sh "oc get route ${appName}-${tag} -n ${projectProd} -o jsonpath='{ .spec.host }' --loglevel=4 > routehost"
            routeHost = readFile('routehost').trim()
        }
        stage('Deploy Beta'){
            echo "Deploy Staging tag ${tag}"
            //deploy
            sh "oc rollout pause dc/${appName}-${tag} -n ${projectProd}"
            sh "oc tag ${projectProd}/${appName}:prod-${env.VERSION} ${projectProd}/${appName}:${tag}"
            sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=config -t configmap --configmap-name=myprestasi-${tag}-cm --mount-path=/opt/app-root/src/application/config/config.php --sub-path=config.php --overwrite"      
            sh "oc rollout resume dc/${appName}-${tag} -n ${projectProd}"
            sh "oc rollout status dc/${appName}-${tag} -w -n ${projectProd}"
        }
        stage("Test Beta") {
            timeout(time:4, unit:'HOURS') {
            input message: "Test deployment: http://${routeHost}. Approve?", id: "approval"
            }
        }
        stage("Go Live") {
            sh "oc rollout pause dc/${appName}-${tag} -n ${projectProd}"
            sh "oc set volume dc/${appName}-${tag} -n ${projectProd} --add --name=config -t configmap --configmap-name=myprestasi-cm --mount-path=/opt/app-root/src/application/config/config.php --sub-path=config.php --overwrite"      
            sh "oc rollout resume dc/${appName}-${tag} -n ${projectProd}"
            sh "oc rollout status dc/${appName}-${tag} -w -n ${projectProd}"
            sh "oc set -n ${projectProd} route-backends ${appName} ${appName}-${tag}=100 ${appName}-${altTag}=0 --loglevel=4"
        }
    }
}
} catch (err) {
    echo "in catch block"
    echo "Caught: ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}