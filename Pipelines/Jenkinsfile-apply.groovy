#!groovy

def channel = '#devops-builds'

properties(
  [[$class: 'GithubProjectProperty', projectUrlStr: 'http://github.com/hmcts/azure-packer-baseimage/']]
)

@Library('Reform') _

node {
  ws('azure-packer-baseimage') { // This must be the name of the role otherwise ansible won't find the role
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

        stage('Verify Syntax') {
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
          
          stage('Log in to Azure') {
            sh '''
              az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET --tenant $AZURE_TENANT_ID
            '''
          }

          stage('Set appropriate Subcription Name') {
            sh '''
	      az account set --subscription $Subscription
            '''
          }

          stage('Ensure RG exists.') {
            sh '''
              az group create -n abrg$Subscription -l uksouth
            '''
          }

          stage('Ensure Storage Account exists.') {
            sh '''
		az storage account create -n absa$(az account show | jq -r .name | tr [:upper:] [:lower:] | sed s#-##g) --resource-group abrg$Subscription -l uksouth --sku Standard_LRS
            '''
          }
          
          stage('Remove Previous images directory') {
            sh '''
              az storage container delete --account-name absa$(az account show | jq -r .name | tr [:upper:] [:lower:] | sed s#-##g) -n images
            '''
          }

          stage('Remove Previous system directory') {
            sh '''
              az storage container delete --account-name absa$(az account show | jq -r .name | tr [:upper:] [:lower:] | sed s#-##g) -n system
            '''
          }

          stage('Sleep for 60 seconds') {
            sh '''
              sleep 60
            '''
          }

          stage('Packer Deploy') {
            sh '''
              packer build -var-file=ansible-management/packer_vars/azure-packer-baseimage.json -var "azure_subscription_id=$(az account show | jq -r .id)" -var "azure_resource_group_name=jabrg$Subscription" -var "azure_storage_account_name=jabsa$(az account show | jq -r .name | tr [:upper:] [:lower:] | sed s#-##g)" azure-centos-gold.json
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
            echo "Nothing to do."
            '''
        }
      }
  }
}
