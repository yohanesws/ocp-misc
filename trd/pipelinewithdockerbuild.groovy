try {
    timeout(time: 20, unit: 'MINUTES') {
      def appName="pocnodejs"
      def projectDev="poc-dev"
      def projectAlpha="poc-alpha"
      def projectProd="poc-prod"
      def projectCicd="cicd"
      def gitRepo="http://git.dmp.true.th/DMP-DevOps/POC-Sample-NodeJS2.git"
      def nonProdRepo="docker-registry-default.os2-np.dmp.true.th"
      def prodRepo="docker-registry-default.os2.dmp.true.th"
      def apiNonProd="ose2-np.dmp.true.th:8443"
      def apiProd="ose2.dmp.true.th:8443"
      def tokenNonProd="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJjaWNkIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImplbmtpbnMtdG9rZW4tMnQ1cnIiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiamVua2lucyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjU4ZDEyNWNkLTIwMmItMTFlOC1iZWMzLTAwNTA1NmIxMTZiYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpjaWNkOmplbmtpbnMifQ.vRhK2yNCbO2ybO1aUTTdKCwCXmOU4yrs17MCi9ZqodI3p34jnLAzC7XZGQPGkWtTgpPiT5rOyDsXEI9C4v0JUThVw7g0myyFPhZpC4jJEWzF1cEeDw4Z1dd-F-vwz2kCs2fof-Zi7hD_aV86QAVjik9KkBN_46OR7h2zP857Fc2-oqs63XSKOXE8tYauqUSSg9CBxUNbUsUxqiRZ15awUXgvbnNPCEWaLF4YLQ4luHSDksVKPXOkhvnOIHzfnCHVqzIo7B8WSVq2miyG2bH2xMEluA9EOJzKyllQd_PPnYqC3QsW6tFxwVi7ww12l8TyIptjX4AEkcCxGDKhgJKHQQ"
      def tokenProd="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJjaWNkIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImNsb3VkYXV0by10b2tlbi1kcTFydCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJjbG91ZGF1dG8iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI4ZWUxYTE2ZC0yMGVlLTExZTgtYjE0MC0wMDUwNTZiMWM1MGYiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6Y2ljZDpjbG91ZGF1dG8ifQ.gPZ9a8CemVU3OoXU1rnll2KYdB2pCNBNi_dlSNz_bPF0ql6wgPon9dJFLZ3rncbJ6iZc21JQNhTouZeltgn0EvLjrEc4wMZJwS0YkZ3fzl0tPXpKYM3AZx7Xl_F_L38sPgL_hFevXppK95bMkneOW89Dc24vWX7WPxQ_3BfwGef6GY5ldbMl0l0sFL9MEhRezw_GD9iYqEevMgfndsBaTHlhGjMnyquFJ9R0tdcwxJIQxmH_7DwQUa-r_YdOb4Aot5qY0RVGrkpwr5eXPrdJtuCrvaalCanAKK4urH-gHKJwXo218LInIQUFCjJ9gCIFNtnkkWTv4P7YZ94oKOldZA"
      def tag="blue"
      def altTag="green"
      def secretName="true-gitlab"


      properties([
        pipelineTriggers([
            pollSCM('H/10 * * * *')
        ])
      ])

      node ('nodejs') {
        stage ("Prepare Build"){
          git branch: "master", url: gitRepo , credentialsId: "${projectCicd}-${secretName}", poll: true, changelog: true
        }
        stage("Build Image") {
          sh "oc login ose2-np.dmp.true.th:8443 --insecure-skip-tls-verify --token=${tokenNonProd}"
          sh "oc project ${projectDev}"
          sh "oc process openshift//generic-docker-build APP_NAME=${appName} GIT_SOURCE_URL=${gitRepo} GIT_SOURCE_REF=master DOCKERFILE_PATH=Dockerfile OPENSHIFT_SECRET_NAME=true-gitlab | oc apply -f - || true"
          sh "oc start-build ${appName} -n ${projectDev} --follow --wait"
          // openshiftVerifyBuild bldCfg: "${appName}", namespace: "${projectDev}", waitTime: '20', waitUnit: 'min', apiURL: "${apiNonProd}", authToken: "${tokenNonProd}"
        }
        stage("Deploy Dev") {
            // clean up. keep the image stream
          sh "oc delete dc,svc,route -l app=${appName} -n ${projectDev}"
          sh "oc new-app ${appName} -n ${projectDev} || true"
          // openshiftVerifyDeployment depCfg: "${appName}", namespace: "${projectDev}", waitTime: '5', waitUnit: 'min', apiURL:"${apiNonProd}", authToken:"${tokenNonProd}"
          sh "oc rollout status dc/${appName} -w -n ${projectDev}"
          sh "oc expose svc/${appName} -n ${projectDev}"
        }
        stage ('Deploy ALPHA') {
            timeout(time:10, unit:'MINUTES') {
              // input message: "Promote to ALPHA?", ok: "Promote", submitter: '"CN=Tumanoon Punjansing,OU=Employees,DC=true,DC=care"'
              input message: "Promote to ALPHA?", ok: "Promote"
            }
            
            // tag for stage
            sh "oc tag ${projectDev}/${appName}:latest ${projectAlpha}/${appName}:alpha"
            // clean up. keep the imagestream
            sh "oc delete bc,dc,svc,route -l app=${appName} -n ${projectAlpha}"
            // deploy stage image
            sh "oc new-app ${appName}:alpha --name=${appName} -n ${projectAlpha}"
          //  openshiftVerifyDeployment depCfg: "${appName}", namespace: "${projectAlpha}", waitTime: '5', waitUnit: 'min', apiURL:"${apiNonProd}", authToken:"${tokenNonProd}"
            sh "oc rollout status dc/${appName} -w -n ${projectAlpha}"
            sh "oc expose svc/${appName} -n ${projectAlpha}"
        }
        stage ('Promote Prod') {
            timeout(time:10, unit:'MINUTES') {
              input message: "Promote to Production?", ok: "Promote"
            }
            sh "oc project cicd"
            // clean the old is
            sh "oc delete is ${appName} -n ${projectCicd} || true"
            // clean the old bc
            sh "oc delete bc ${appName}-image-mover -n ${projectCicd} || true"
            // create build configuration to push to production
            sh "oc new-build --strategy=docker --dockerfile=\"FROM docker-registry.default.svc:5000/${projectAlpha}/${appName}:alpha\" --to-docker=true --to=\"${prodRepo}/${projectProd}/${appName}:ready\" --name=${appName}-image-mover --allow-missing-images -n ${projectCicd}"
            // set build to use prod credential in secret
            sh "oc patch bc ${appName}-image-mover -p '{\"spec\":{\"output\":{\"pushSecret\":{\"name\":\"prod-registry\"}}}}' -n ${projectCicd}"
            // promote the image
            sh "oc start-build ${appName}-image-mover --follow --wait -n ${projectCicd}"
            
        }
      }
      node ('prod'){
        stage ('Preparing Deploy') {
            timeout(time:10, unit:'MINUTES') {
              input message: "Deploy to Production?", ok: "Deploy"
            }
            sh "oc login ose2.dmp.true.th:8443 --insecure-skip-tls-verify --token=${tokenProd}"
            // clean up.
            // sh "oc delete is,bc,dc,svc,route -l app=${appName} -n ${projectProd}"
            // import image
            // sh "oc import-image ${appName}:prod --from=${nonProdRepo}/${projectAlpha}/${appName}:alpha --confirm -n ${projectProd}"
            // deploy prod image
            // sh "oc new-app ${appName}:prod --name=${appName} -n ${projectProd}"
            //  openshiftVerifyDeployment depCfg: "${appName}", namespace: "${projectProd}", waitTime: '5', waitUnit: 'min', apiURL:"${apiProd}", authToken:"${tokenProd}"
            // sh "oc rollout status dc/${appName} -w -n ${projectProd}"
            // sh "oc expose svc/${appName} -n ${projectProd}"
            sh "oc get is ${appName} -n ${projectProd} -o jsonpath='{.status.tags[*].tag}' --loglevel=4 > imagetag"
            imagetag = readFile('imagetag').trim()
            echo "find image tage ${imagetag}"
            if (!imagetag.contains(tag)){
              sh "oc tag ${projectProd}/${appName}:ready ${projectProd}/${appName}:${tag}"
              sh "oc new-app ${appName}:${tag} --name=${appName}-${tag} -n ${projectProd} "
              sh "oc expose svc/${appName}-${tag} -n ${projectProd}"
              sh "oc expose svc/${appName}-${tag} --name=${appName} -n ${projectProd}"
              sh "oc set -n ${projectProd} route-backends ${appName} ${appName}-${tag}=100 --loglevel=4"
            }
            if (!imagetag.contains(altTag)){
              sh "oc tag ${projectProd}/${appName}:ready ${projectProd}/${appName}:${altTag}"
              sh "oc new-app ${appName}:${altTag} --name=${appName}-${altTag} -n ${projectProd}"
              sh "oc expose svc/${appName}-${altTag} -n ${projectProd}"
              sh "oc delete route ${appName} -n ${projectProd}"
              sh "oc expose svc/${appName}-${tag} --name=${appName} -n ${projectProd}"
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
          stage('Deploy Staging'){
            echo "Deploy Staging tag ${tag}"
            //backup
            sh "oc tag ${projectProd}/${appName}:${tag} ${projectProd}/${appName}:${tag}-old"
            //deploy
            sh "oc tag ${projectProd}/${appName}:ready ${projectProd}/${appName}:${tag}"
          }
          stage("Test Staging") {
            timeout(time:10, unit:'MINUTES') {
              input message: "Test deployment: http://${routeHost}. Approve?", id: "approval"
            }
          }
          stage("Go Live") {
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