#!groovy

def channel = '#devops-builds'

properties(
  [[$class: 'GithubProjectProperty', projectUrlStr: 'http://github.com/hmcts/azure-packer-baseimage/']]
)

@Library('Reform') _

node {

//          checkout([$class: 'GitSCM', branches: scm.branches, doGenerateSubmoduleConfigurations: true, extensions: scm.extensions + [[$class: 'SubmoduleOption', parentCredentials: true]], userRemoteConfigs: scm.userRemoteConfigs])


  ws('azure-packer-baseimage') {
    try {
      wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {

        stage('Checkout') {
          checkout scm
          dir('bootstrap-role') {
            git url: "git@github.com/hmcts/bootstrap-role.git", branch: "master"
          }
          dir('ansible-management') {
            git url: "https://github.com/hmcts/ansible-management", branch: "master", credentialsId: "jenkins-public-github-api-token"
          }
        }

        stage('Role Installation/Download') {
          sh '''
            ansible-galaxy install -r bootstrap-role/requirements.yml --force --roles-path=bootstrap-role/roles/
          '''
        }

        stage('Packer Version') {
          sh '''
            packer version
          '''
        }

        stage('Initial Verify Syntax') {
          sh '''
            packer validate -var "azure_client_id=ug" -var "azure_client_secret=ogg" -var "azure_subscription_id=laurel" -var "azure_resource_group_name=adam" -var "azure_storage_account_name=eve" azure-centos-gold.json
          '''
        } 

        withCredentials([
            [$class: 'StringBinding', credentialsId: 'IDAM_ARM_CLIENT_SECRET', variable: 'AZURE_CLIENT_SECRET'],
            [$class: 'StringBinding', credentialsId: 'IDAM_ARM_CLIENT_ID', variable: 'AZURE_CLIENT_ID'],
            [$class: 'StringBinding', credentialsId: 'IDAM_ARM_TENANT_ID', variable: 'AZURE_TENANT_ID'],
            [$class: 'StringBinding', credentialsId: 'IDAM_ARM_SUBSCRIPTION_ID', variable: 'AZURE_SUBSCRIPTION_ID']
        ]) {
          
          stage('Log in to Azure for next steps') {
            sh '''
              az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
            '''
          }

          stage('Set appropriate Subcription ID') {
            sh '''
              az account set --subscription $AZURE_SUBSCRIPTION_ID
            '''
          }

          stage('Show associated Subscription ID Name') {
            sh '''
              az account show | jq -r .name
            '''
          }

          stage('Wait because Azure sucks.') {
            sh '''
              sleep 30
            '''
          }

					}

        }

    } catch (err) {
      notifyBuildFailure channel: "${channel}"
      throw err
    } finally {
      stage('Cleanup') {
          sh '''
            echo "Nothing to do for Cleanup."
            '''
        }
      }
  }
}
