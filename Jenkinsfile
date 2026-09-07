// ============================================================
// SmartFix - initial DevSecOps baseline (Jenkins declarative)
// ------------------------------------------------------------
// This pipeline ONLY contains stages that can actually run today:
// checkout, compile, unit tests, packaging.
//
// The automated unit tests use the isolated H2 test profile
// (application-test.yml), so no database is required on the agent.
//
// TODO(Sprint 4/5) - future stages to be added ONLY when the tools
// are actually configured for the project. Do NOT add fabricated
// integrations:
//   - Static Code Analysis      (e.g. SonarQube)
//   - Software Composition Analysis (e.g. OWASP Dependency-Check)
//   - Security Scanning         (e.g. Trivy / container scanning)
//   - Docker build + publish
//   - Deployment
//
// Expected Jenkins setup: an agent that has JDK 21 and Maven 3.9+
// available on PATH (or a configured `maven` tool under "Manage
// Jenkins -> Tools"). See docs/development-guide.md.
// ============================================================

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                // Compile + validate configuration without running tests yet.
                sh 'mvn -B -DskipTests clean compile'
            }
        }

        stage('Unit Test') {
            steps {
                // Runs the JUnit 5 suite against the H2 test profile.
                sh 'mvn -B test'
            }
        }

        stage('Package') {
            steps {
                // Produces the runnable Spring Boot fat jar.
                sh 'mvn -B -DskipTests package'
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        failure {
            // TODO: add notification (e.g. email/Slack) once decided by the team.
            echo 'SmartFix build failed - see stage logs.'
        }
    }
}
