try {
    timeout(time: 20, unit: 'MINUTES') {
      def appName="test"
      def projectTarget="sakti-app"
      def projectCicd="devops"
      def gitRepo="http://root@10.100.44.242/root/sakti.git"
      def branch="master"
      def secretName="gitlab-perben"
      def version=""


      properties([
        pipelineTriggers([
            pollSCM('H/5 * * * *')
        ])
      ])

      node(){
          stage('Preparing'){
            git branch: branch, url: gitRepo , credentialsId: "${projectCicd}-${secretName}", poll: true, changelog: true
          }
      }

      node ('maven') {
        stage("Cloning"){
          git branch: branch, url: gitRepo , credentialsId: "${projectCicd}-${secretName}"
          pom = readMavenPom file: 'pom.xml'
          version=pom.version
          if (version == "")
            version="latest"
          echo "version : ${version}"
        }
        stage ("Unit Test"){
          sh "mvn test"
          // step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
        }
        // stage("Code Analysis") {
        //    sh "mvn sonar:sonar -Dsonar.host.url=${sonarqube} -Dsonar.login=${sonarqubeToken} -Dsonar.projectName=test -DskipTests=true"
        // } 
        stage("Build the Source") {
          sh "mvn clean install -DskipTests=true"
          sh "rm -rf oc-build && mkdir -p oc-build/deployments"
          sh "cp target/*.jar oc-build/deployments/"
          sh "cp -r configuration oc-build/configuration || true"
          sh "cp -r modules oc-build/modules || true"
        }
        stage("Build Image") {
          sh "oc project ${projectTarget}"
          //create EAP S2I Binary Build
          sh "oc new-build openshift/jboss-eap71-openshift:1.3 --name=${appName} --binary  -n ${projectTarget} || true"
          // start build
          sh "oc start-build ${appName} -n ${projectTarget} --from-dir=oc-build --follow --wait"
          sh "oc tag ${projectTarget}/${appName}:latest ${projectTarget}/${appName}:${version}"
        }
        // stage ("Configuring") {
        //  def cmNotPresent = false
        //  try {
        //    sh "oc get cm ${appName}-${version}-cm -o jsonpath='{.metadata.name}' -n ${projectTarget}"
        //  } catch(err) {
        //    cmNotPresent = true
        //  }
        //  if (cmNotPresent){
        //    sh "oc delete cm,secret -l app=${appName}-${version} -n ${projectTarget} || true"
        //    sh "rm -rf files-config && mkdir -p files-config"
        //    sh "chmod -R 777 src/main/resources"
        //    sh "cp src/main/resources/*.properties files-config/ || true"

            //creating configmap
        //  sh "oc create cm ${appName}-${version}-cm --from-file=files-config/ -n ${projectTarget}"
        //  sh "oc label cm/${appName}-${version}-cm app=${appName}-${version} -n ${projectTarget}"
        //  }
        // }
        stage("Deploying") {
          def dcNotPresent = false
          try {
            sh "oc get dc ${appName}-${version} -o jsonpath='{.metadata.name}' -n ${projectTarget}"
          }catch(err){
            dcNotPresent = true
          }

          if (dcNotPresent){
            sh "oc tag ${projectTarget}/${appName}:${version} ${projectTarget}/${appName}:uat-${version}"
            sh "oc new-app --docker-image=docker-registry.default.svc:5000/${projectTarget}/${appName}:uat-${version} --name=${appName}-${version} -n ${projectTarget} || true"
            sh "oc rollout cancel dc/${appName}-${version} -n ${projectTarget} || true"
            sh "oc rollout pause dc/${appName}-${version} -n ${projectTarget}"  
            // sh "oc set volume dc/${appName}-${version} -n ${projectTarget} --add --name=config-volume -t configmap --configmap-name=${appName}-${version}-cm --mount-path=/deployments/config || true"
            // if (secretDb !=  ""){
            //    sh "oc set env --from=secret/${appName}-${version}-db --prefix=SPRING_DATASOURCE_ dc/${appName}-${version} -n ${projectTarget}"
            // }
            //apply timezone
            sh "oc set env dc/${appName}-${version} TZ=Asia/Bangkok -n ${projectTarget}"

            sh "oc rollout resume dc/${appName}-${version} -n ${projectTarget}"  
            sh "oc rollout status dc/${appName}-${version} -w -n ${projectTarget}"

            sh "oc create route edge ${appName}-${version}  --service='${appName}-${version}' -n ${projectTarget}"
            
          }else{
            sh "oc rollout pause dc/${appName}-${version} -n ${projectTarget}"  
            sh "oc tag ${projectTarget}/${appName}:${version} ${projectTarget}/${appName}:uat-${version}"
            sh "oc rollout resume dc/${appName}-${version} -n ${projectTarget}"  
            sh "oc rollout status dc/${appName}-${version} -w -n ${projectTarget}"
            echo "just update the image"
          }
        }
      }
  }
} catch (err) {
    echo "in catch block"
    echo "Caught: ${err}"
    currentBuild.result = 'FAILURE'
    throw err
} // JenkinsPipeline