try {
    timeout(time: 20, unit: 'MINUTES') {
      def appName="pocjavabin"
      def projectTarget="poc-dev"
      def projectCicd="cicd"
      def gitRepo="http://git.dmp.true.th/DMP-DevOps/OC-Sample-JavaSpring2.git"
      def tag="blue"
      def altTag="green"
      def secretName="true-gitlab"
      def branch="master"
      def version=""
      def needroute=false
      def secretDb="db-secret"
      def springconfig=""


      properties([
        pipelineTriggers([
            pollSCM('H/10 * * * *')
        ])
      ])

      node ('maven') {
        stage("Cloning"){
          sh "git config --global http.sslVerify false"
          git branch: branch, url: gitRepo , credentialsId: "${projectCicd}-${secretName}", poll: true, changelog: true
          sh "git for-each-ref --sort=-taggerdate --count=1 --format '%(refname:short)' refs/tags > version.txt"
          version= readFile(version.txt)
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
          sh "oc new-build openshift/redhat-openjdk18-openshift --name=${appName} --binary  -n ${projectDev} || true"
          // start build
          sh "oc start-build ${appName} -n ${projectTarget} --from-file=oc-build/deployments/APP.jar --follow --wait"
          sh "oc tag ${projectTarget}/${appName}:latest ${projectTarget}/${appName}:${version}"
        }
        stage {"Configuring"} {
          sh "oc delete cm,secret -l app=${appName}-${version} -n ${projectTarget} "
          sh "rm -rf files-config && mkdir -p files-config"
          sh "cp src/main/resources/*.properties files-config/ || true"
          sh "cp src/main/resources/*.yml files-config/ || true"
          sh "src/main/resources/*.yaml files-config/ || true"
          sh "src/main/resources/*.json files-config/ || true"
          sh "src/main/resources/*.xml files-config/ || true"

          //creating configmap
          sh "oc create cm ${appName}-${version}-cm --from-file=files-config/ -n ${projectTarget}"
          sh "oc label cm/${appName}-${version}-cm app=${appName}-${version} -n ${projectTarget}"

          if (secretDb !=  ""){
            //copying secret db
            sh "oc export secret/${secretDb}-${version} -n ${projectCicd} | oc apply -n ${projectTarget} -f -"
          }
        }
        stage("Deploy Dev") {
          // clean up. keep the image stream
          sh "oc delete dc,svc,route -l app=${appName}-${version} -n ${projectTarget}"
          sh "oc new-app ${appName}:${version} --name=${appName}-${version} -n ${projectTarget} || true"
          sh "oc rollout cancel ${appName}-${version} -n ${projectTarget} || true"
          sh "oc rollout pause ${appName}-${version} -n ${projectTarget}"  
          sh "oc set volume dc/${appName}-${version} -n ${projectTarget} --add --name=config-volume -t configmap --configmap-name=${appName}-${version}-cm --mount-path=/deployments/config || true"
          if (secretDb !=  ""){
              sh "oc set env --from=configmap/myconfigmap --prefix=SPRING_DATASOURCE_"
          }
          //apply timezone
          sh "oc set env dc/${appName}-${version} TZ=Asia/Tokyo -n ${projectTarget}"
          //apply default server port
          sh "oc set env dc/${appName}-${version} SERVER_PORT=8080 -n ${projectTarget}"
          //configure central
          if (springconfig != ""){
            sh "oc set env dc/${appName}-${version} SPRING_CLOUD_CONFIG_URI=${springconfig} -n ${projectTarget}"
          }
          sh "oc rollout status dc/${appName}-${version} -w -n ${projectTarget}"
          if (needroute)
            sh "oc expose svc/${appName}-${version} -n ${projectTarget}"
        }
      }
  }
} catch (err) {
    echo "in catch block"
    echo "Caught: ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}