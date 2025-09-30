pipeline {
    agent any

    tools {
        maven 'Maven 3.8.6'
    }

    stages {
        stage('Checkout') {
            steps {
                git 'https://github.com/NullByte3/AuctionBackend.git'
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean install'
            }
        }
    }
}