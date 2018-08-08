try {
    timeout(time: 20, unit: 'MINUTES') {
      def appName="centralize-configuration"
      def projectTarget="application-sit"
      def projectCicd="cicd"
      def gitRepo="https://git.raindrop.cimbthai.com/platform/centralize-configuration.git"
      def branch="master"
      def secretName="gitlab-cimb"
      def version=""
      def needroute=false
      def secretDb="central-config-secret"
      def springconfig=""
      def bluegreen=false


      properties([
        pipelineTriggers([
            pollSCM('H/10 * * * *')
        ])
      ])

      node ('maven') {
        stage("Cloning"){
          sh "git config --global http.sslVerify false"
          git branch: branch, url: gitRepo , credentialsId: "${projectCicd}-${secretName}", poll: true, changelog: true
          sh "git for-each-ref --sort=taggerdate --count=1 --format '%(refname:short)' refs/tags | tr -d '\n'| tr '.' '-'> latesttag.txt"
          //sh "cat latesttag.txt|  tr -d '\n' > tag.txt"
          version=readFile('latesttag.txt')
          if (version == "")
            version="latest"
          echo "version : ${version}"
        }
        stage("Build the Source") {
          sh "mvn clean install -DskipTests=true"
          sh "rm -rf oc-build && mkdir -p oc-build/deployments"
          sh "cp target/*.jar oc-build/deployments/APP.jar"
        }
        stage("Build Image") {
          sh "oc project ${projectTarget}"
          //create JAVA S2I Binary Build
          sh "oc new-build openshift/redhat-openjdk18-openshift:1.4 --name=${appName} --binary  -n ${projectTarget} || true"
          // start build
          sh "oc start-build ${appName} -n ${projectTarget} --from-file=oc-build/deployments/APP.jar --follow --wait"
          sh "oc tag ${projectTarget}/${appName}:latest ${projectTarget}/${appName}:${version}"
        }
        stage ("Configuring") {
          sh "rm -rf files-config && mkdir -p files-config"
          sh "chmod -R 777 src/main/resources"
          sh "cp src/main/resources/*.properties files-config/ || true"
          sh "cp src/main/resources/*.yml files-config/ || true"
          sh "cp src/main/resources/*.yaml files-config/ || true"
          sh "cp src/main/resources/*.json files-config/ || true"
          sh "cp src/main/resources/*.xml files-config/ || true"

          //creating configmap
          sh "oc create cm ${appName}-${version}-cm --from-file=files-config/ -n ${projectTarget}"
          sh "oc label cm/${appName}-${version}-cm app=${appName}-${version} -n ${projectTarget}"

          if (secretDb !=  ""){
            //copying secret db
            sh "oc get secret ${secretDb} -n ${projectCicd} -o template --template='{{.data.password}}'|base64 -d|tr -d '\n' > password.secret"
            sh "oc get secret ${secretDb} -n ${projectCicd} -o template --template='{{.data.username}}'|base64 -d|tr -d '\n' > username.secret"
            sh "oc get secret ${secretDb} -n ${projectCicd} -o template --template='{{.data.url}}'|base64 -d|tr -d '\n' > url.secret"
            sh "ls"
            sh "cat password.secret"
            def password=readFile('password.secret')
            def username=readFile('username.secret')
            def url=readFile('url.secret')
            sh "rm -f *.secret"
            sh "oc create secret generic ${appName}-${version}-db --from-literal=username=${username} --from-literal=password=${password} --from-literal=url=${url} -n ${projectTarget} ||true"
            sh "oc label secret/${appName}-${version}-db app=${appName}-${version} -n ${projectTarget} || true"
          }
        }
        if(bluegreen){

        }else{
          stage("Deploy Uat") {
            sh "oc new-app --docker-image=docker-registry.default.svc:5000/${projectTarget}/${appName}:${version} --name=${appName}-${version} -n ${projectTarget} || true"
            sh "oc rollout cancel dc/${appName}-${version} -n ${projectTarget} || true"
            sh "oc rollout pause dc/${appName}-${version} -n ${projectTarget}"  
            sh "oc set volume dc/${appName}-${version} -n ${projectTarget} --add --name=config-volume -t configmap --configmap-name=${appName}-${version}-cm --mount-path=/deployments/config || true"
            if (secretDb !=  ""){
                sh "oc set env --from=secret/${appName}-${version}-db --prefix=SPRING_DATASOURCE_ dc/${appName}-${version} -n ${projectTarget}"
            }
            //apply timezone
            sh "oc set env dc/${appName}-${version} TZ=Asia/Bangkok -n ${projectTarget}"
            //apply default server port
            sh "oc set env dc/${appName}-${version} SERVER_PORT=8080 -n ${projectTarget}"
            //configure central
            if (springconfig != ""){
              sh "oc set env dc/${appName}-${version} SPRING_CLOUD_CONFIG_URI=${springconfig} -n ${projectTarget}"
            }
            sh "oc rollout resume dc/${appName}-${version} -n ${projectTarget}"  
            sh "oc rollout status dc/${appName}-${version} -w -n ${projectTarget}"
            if (needroute)
              sh "oc expose svc/${appName}-${version} -n ${projectTarget}"
          }
        }
        
      }
  }
} catch (err) {
    echo "in catch block"
    echo "Caught: ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}
