// Sprint 2 CI. Requires JDK 21 and Maven 3.9+ on the Jenkins agent.
// PostgreSQL runs only against an explicitly configured dedicated *_test database.
// Supply TEST_DB_URL, TEST_DB_USERNAME and TEST_DB_PASSWORD through Jenkins credentials.
pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    parameters {
        booleanParam(name: 'RUN_POSTGRES_IT', defaultValue: false,
            description: 'Run MigrationIT using the dedicated PostgreSQL test database')
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    if (params.RUN_POSTGRES_IT) {
                        currentBuild.description = 'PostgreSQL: requested; external security tools: NOT ENABLED'
                    } else {
                        currentBuild.description = 'PostgreSQL: NOT RUN; external security tools: NOT ENABLED'
                    }
                }
            }
        }
        stage('Build') {
            steps { sh 'mvn -B clean compile' }
        }
        stage('Unit Test') {
            steps { sh 'mvn -B test' }
        }
        stage('Package') {
            steps { sh 'mvn -B package' }
        }
        stage('Verify') {
            steps { sh 'mvn -B clean verify' }
        }
        stage('PostgreSQL Integration') {
            when { expression { params.RUN_POSTGRES_IT } }
            steps { sh 'mvn -B -Ppostgres-it verify' }
        }
        stage('Security') {
            // This stage is visibly SKIPPED until a real scanner is selected and configured.
            when { expression { false } }
            steps { error('Configure a real scanner and its failure policy before enabling this stage.') }
        }
    }
    post {
        always {
            // Missing reports fail the build; absence never means tests passed.
            junit testResults: 'target/surefire-reports/TEST-*.xml'
            junit testResults: 'target/failsafe-reports/TEST-*.xml'
        }
        success {
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }
        failure {
            echo 'SmartFix verification failed. Inspect the failing stage and its reports.'
        }
    }
}
