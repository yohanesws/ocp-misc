try {
    timeout(time: 20, unit: 'MINUTES') {
      def projectDev="poc-dev"
      def projectAlpha="poc-alpha"
      def projectProd="poc-prod"
      def projectCicd="cicd"
      def apiNonProd="ose2-np.dmp.true.th:8443"
      def apiProd="ose2.dmp.true.th:8443"
      def tokenNonProd="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJjaWNkIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImplbmtpbnMtdG9rZW4tMnQ1cnIiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiamVua2lucyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjU4ZDEyNWNkLTIwMmItMTFlOC1iZWMzLTAwNTA1NmIxMTZiYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpjaWNkOmplbmtpbnMifQ.vRhK2yNCbO2ybO1aUTTdKCwCXmOU4yrs17MCi9ZqodI3p34jnLAzC7XZGQPGkWtTgpPiT5rOyDsXEI9C4v0JUThVw7g0myyFPhZpC4jJEWzF1cEeDw4Z1dd-F-vwz2kCs2fof-Zi7hD_aV86QAVjik9KkBN_46OR7h2zP857Fc2-oqs63XSKOXE8tYauqUSSg9CBxUNbUsUxqiRZ15awUXgvbnNPCEWaLF4YLQ4luHSDksVKPXOkhvnOIHzfnCHVqzIo7B8WSVq2miyG2bH2xMEluA9EOJzKyllQd_PPnYqC3QsW6tFxwVi7ww12l8TyIptjX4AEkcCxGDKhgJKHQQ"
      def tokenProd="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJjaWNkIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImNsb3VkYXV0by10b2tlbi1kcTFydCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJjbG91ZGF1dG8iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI4ZWUxYTE2ZC0yMGVlLTExZTgtYjE0MC0wMDUwNTZiMWM1MGYiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6Y2ljZDpjbG91ZGF1dG8ifQ.gPZ9a8CemVU3OoXU1rnll2KYdB2pCNBNi_dlSNz_bPF0ql6wgPon9dJFLZ3rncbJ6iZc21JQNhTouZeltgn0EvLjrEc4wMZJwS0YkZ3fzl0tPXpKYM3AZx7Xl_F_L38sPgL_hFevXppK95bMkneOW89Dc24vWX7WPxQ_3BfwGef6GY5ldbMl0l0sFL9MEhRezw_GD9iYqEevMgfndsBaTHlhGjMnyquFJ9R0tdcwxJIQxmH_7DwQUa-r_YdOb4Aot5qY0RVGrkpwr5eXPrdJtuCrvaalCanAKK4urH-gHKJwXo218LInIQUFCjJ9gCIFNtnkkWTv4P7YZ94oKOldZA"
      def tag="blue"
      def altTag="green"
      def appName
      def env

      stage('Rollback Input') {
      def userInput = input(
        id: 'userInput', message: 'Rollback?', parameters: [
        // [$class: 'TextParameterDefinition', defaultValue: 'prod', description: 'Environment', name: 'ENV'],
        [$class: 'ChoiceParameterDefinition', choices: 'prod\nalpha\ndev', description: 'Environment', name: 'ENV'],
        [$class: 'StringParameterDefinition', defaultValue: 'pocjavas2i', description: 'Target', name: 'APP_NAME']
        // [ 
        //     $class: 'BooleanParameterDefinition',
        //     defaultValue: true,
        //     name: 'Run test suites?',
        //     description: 'A checkbox option'
        //   ],
        //   [ 
        //     $class: 'PasswordParameterDefinition',
        //     defaultValue: "MyPasswd",
        //     name: 'Enter a password',
        //     description: 'A password option'
        //   ],
      ])
      env = userInput.ENV
      appName = userInput.APP_NAME
    }

      switch (env) {
        case "prod":
          node('prod'){
            stage('Initialize') {
              sh "oc login ${apiProd} --insecure-skip-tls-verify --token=${tokenProd}"
              sh "oc get route ${appName} -n ${projectProd} -o jsonpath='{ .spec.to.name }' --loglevel=4 > activeservice"
              activeService = readFile('activeservice').trim()
              // if active service is green then we rollback green to altTag which is blue
              if (activeService == "${appName}-green") {
                tag = "green"
                altTag = "blue"
              }
              sh "oc get is ${appName} -n ${projectProd} -o jsonpath='{.status.tags[*].tag}' --loglevel=4 > imagetag"
              imagetag = readFile('imagetag').trim()
              echo "find image tage ${imagetag}"
            }
            stage('Rollback Prod'){
              timeout(time:10, unit:'MINUTES') {
              // input message: "Promote to ALPHA?", ok: "Promote", submitter: '"CN=Tumanoon Punjansing,OU=Employees,DC=true,DC=care"'
                input message: "Rollback ${activeService} to ${appName}-${altTag}?", ok: "Rollback"
              }
              echo "trying rollback ${activeService} to ${appName}-${altTag}"
              sh "oc set -n ${projectProd} route-backends ${appName} ${appName}-${altTag}=100 ${appName}-${tag}=0 --loglevel=4"
              if (imagetag.contains(tag+"-old")){
                // rollback the image
                sh "oc tag ${projectProd}/${appName}:${tag}-old ${projectProd}/${appName}:${tag}"
                // remove backup image
                sh "oc tag -d ${projectProd}/${appName}:${tag}-old"
              }else {
                echo "no image to rollback"
              }
            }
          }
          break
        case "alpha":
          node{
            stage('Rollback Alpha') {
              timeout(time:10, unit:'MINUTES') {
              // input message: "Promote to ALPHA?", ok: "Promote", submitter: '"CN=Tumanoon Punjansing,OU=Employees,DC=true,DC=care"'
                input message: "Rollback Alpha?", ok: "Rollback"
              }
              echo "trying to rollback Alpha"
              sh "oc login ${apiNonProd} --insecure-skip-tls-verify --token=${tokenNonProd}"
              sh "oc get is ${appName} -n ${projectAlpha} -o jsonpath='{.status.tags[*].tag}' --loglevel=4 > imagetag-${appName}-${projectAlpha}"
              imagetag = readFile("imagetag-${appName}-${projectAlpha}").trim()
              echo "find image tag ${imagetag}"
              if (imagetag.contains("old")){
                // rollback the image
                sh "oc tag ${projectAlpha}/${appName}:old ${projectAlpha}/${appName}:alpha"
                // remove backup image
                sh "oc tag -d ${projectAlpha}/${appName}:old"
              }else {
                  echo "no image to rollback"
              }
            }
          }
          break
        case "dev":
          node{
            stage ('Rollback Dev'){
              timeout(time:10, unit:'MINUTES') {
              // input message: "Promote to ALPHA?", ok: "Promote", submitter: '"CN=Tumanoon Punjansing,OU=Employees,DC=true,DC=care"'
                input message: "Rollback Dev?", ok: "Rollback"
              }
              echo "trying to rollback Dev"
              sh "oc login ${apiNonProd} --insecure-skip-tls-verify --token=${tokenNonProd}"
              sh "oc get is ${appName} -n ${projectDev} -o jsonpath='{.status.tags[*].tag}' --loglevel=4 > imagetag-${appName}-${projectDev}"
              imagetag = readFile("imagetag-${appName}-${projectDev}").trim()
              echo "find image tag ${imagetag}"
              if (imagetag.contains("old")){
                // rollback the image
                sh "oc tag ${projectDev}/${appName}:old ${projectDev}/${appName}:latest"
                // remove backup image
                sh "oc tag -d ${projectDev}/${appName}:old"
              }else {
                  echo "no image to rollback"
              }
            }
          }
          break
        default:
         echo "nothing to do"
      }
    }
} catch (err) {
    echo "in catch block"
    echo "Caught: ${err}"
    currentBuild.result = 'FAILURE'
    throw err
}