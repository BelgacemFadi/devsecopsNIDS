// ==========================================================================
// DevSecOps CI/CD Pipeline - student-management (devopsNIDS repo)
// ESPRIT 5th year - same validated 7-stage pattern as the demo pipeline:
//   Git -> Jenkins -> SonarQube (SAST + quality gate) -> Nexus (artifacts)
//     -> Docker Hub (images) -> Deploy + smoke test on the VM
//
// DIFFERENCES vs the demo pipeline (devsecops-cicd-lab):
//   - pom.xml sits at the REPO ROOT (no app/ subfolder) -> no dir('app')
//   - branch is 'master', not 'main'
//   - this app NEEDS MySQL: stage 7 boots a mysql:8.0 sidecar container,
//     both containers talk over a docker network
//   - app listens on 8089 with context path /student
//     (from src/main/resources/application.properties) -> port map 8090:8089
//
// BEFORE FIRST RUN:
//   1) DOCKERHUB_USER below = your Docker Hub username
//   2) pom.xml: bump sonar-maven-plugin 3.9.1.2184 -> 5.0.0.4389
//      (5.0.0.4389 is PROVEN against our SonarQube server in the lab;
//      scanner 3.9.x is too old for recent SonarQube servers)
//   3) credentials, same IDs as the demo pipeline:
//      github-creds | sonar-token | nexus-creds | dockerhub-creds
//
// QUOTING RULE (cost us one build in the demo: empty Sonar project key):
//   variables from the `def` block are GROOVY variables, NOT shell env vars.
//   Use them in DOUBLE-quoted strings so Groovy substitutes them:
//       sh "mvn ... -Dsonar.projectKey=${SONAR_PROJECT}"
//   Inside single quotes sh '...' hands ${VAR} to bash verbatim, and bash
//   expands it to EMPTY unless VAR is a real environment variable
//   (injected by withCredentials, or by Jenkins like BUILD_NUMBER).
//
// The job reads this file FROM GIT: every edit here = commit + push,
// then Build Now again.
// ==========================================================================

def DOCKERHUB_USER = 'belgacemfadi1'   // <<< CHANGE ME
def GIT_REPO       = 'https://github.com/BelgacemFadi/devsecopsNIDS.git'  // your fork (renamed at fork time)
def APP_NAME       = 'student-management'
def SONAR_PROJECT  = 'student-management'
def NEXUS_URL      = 'http://localhost:8081'

pipeline {

    agent any

    tools {
        jdk   'jdk17'    // JDK used to build the Spring Boot app
        maven 'maven'    // Maven installed on the VM (/usr/share/maven)
    }

    stages {

        // ------------------------------------------------------------------
        // STAGE 1 - Source Code Management: pull the code from GitHub
        // ------------------------------------------------------------------
        stage('1. Checkout from GitHub') {
            steps {
                git branch: 'master',
                    url: "${GIT_REPO}",
                    credentialsId: 'github-creds'
            }
        }

        // ------------------------------------------------------------------
        // STAGE 2 - Build.
        // -DskipTests on purpose: the generated @SpringBootTest context test
        // needs a LIVE MySQL, which a bare build stage does not have.
        // Upgrade path (report material): add H2 as a test-scoped dependency
        // + a test profile, then drop -DskipTests and let JUnit publish here.
        // ------------------------------------------------------------------
        stage('2. Build with Maven') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
            post {
                always {
                    // Test trends appear as soon as tests are enabled
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        // ------------------------------------------------------------------
        // STAGE 3 - Static code analysis (SAST). withSonarQubeEnv injects
        // SONAR_HOST_URL + SONAR_TOKEN from Manage Jenkins > System.
        // Double quotes REQUIRED: Groovy must substitute ${SONAR_PROJECT}
        // before bash sees the command - single quotes = empty project key.
        // ------------------------------------------------------------------
        stage('3. SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh "mvn sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT} -Dsonar.projectName=${SONAR_PROJECT}"
                }
            }
        }

        // ------------------------------------------------------------------
        // STAGE 4 - Quality Gate. SonarQube pushes the verdict back through
        // the webhook (Administration > Configuration > Webhooks ->
        // http://192.168.33.10:8080/sonarqube-webhook/).
        // ------------------------------------------------------------------
        stage('4. Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ------------------------------------------------------------------
        // STAGE 5 - Artifact repository: publish the SNAPSHOT jar to Nexus
        // (pom version is 0.0.1-SNAPSHOT -> maven-snapshots repository).
        // Two shell steps on purpose:
        //   - heredoc SINGLE-quoted: NEXUS_USER / NEXUS_PASS ARE real env
        //     variables injected by withCredentials
        //   - mvn line DOUBLE-quoted: NEXUS_URL is a def variable
        // ------------------------------------------------------------------
        stage('5. Publish to Nexus') {
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'nexus-creds',
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASS')]) {
                    sh '''
                      cat > /tmp/nexus-settings.xml <<SETTINGS
<settings>
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>${NEXUS_USER}</username>
      <password>${NEXUS_PASS}</password>
    </server>
  </servers>
</settings>
SETTINGS
                    '''
                    sh "mvn deploy -DskipTests -s /tmp/nexus-settings.xml -DaltSnapshotDeploymentRepository=nexus-snapshots::${NEXUS_URL}/repository/maven-snapshots/"
                }
            }
        }

        // ------------------------------------------------------------------
        // STAGE 6 - Container image: build + push to Docker Hub.
        // docker.build() uses the Dockerfile already at the repo root
        // (temurin 17 + target/*.jar - kept from the original repo).
        // toLowerCase(): Docker Hub namespaces must be lowercase.
        // ------------------------------------------------------------------
        stage('6. Build and Push Docker Image') {
            steps {
                script {
                    def img = docker.build("${DOCKERHUB_USER.toLowerCase()}/${APP_NAME}:${BUILD_NUMBER}")
                    docker.withRegistry('https://index.docker.io/v1/', 'dockerhub-creds') {
                        img.push()
                        img.push('latest')
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // STAGE 7 - Deployment + smoke test, now with a database.
        // application.properties says: server.port=8089, context-path=/student
        // -> container port 8089 mapped to VM port 8090.
        // Spring relaxed binding: SPRING_DATASOURCE_* env vars OVERRIDE
        // application.properties - that is how the app points at the
        // mysql-db container instead of localhost.
        // Quoting layout: every block with bash $variables is SINGLE-quoted
        // (no Groovy vars inside); every block using def vars is DOUBLE.
        // ------------------------------------------------------------------
        stage('7. Deploy and Smoke Test') {
            steps {
                // (a) fresh network + database
                sh """
                  docker network create appnet 2>/dev/null || true
                  docker rm -f mysql-db student-app 2>/dev/null || true
                  docker run -d --name mysql-db --network appnet --restart unless-stopped -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=studentdb mysql:8.0
                """
                // (b) wait until MySQL accepts connections (single quotes:
                //     bash $variables, no Groovy vars here)
                sh '''
                  echo "Waiting for MySQL to be ready..."
                  for i in $(seq 1 30); do
                    if docker exec mysql-db mysqladmin ping -uroot -proot --silent 2>/dev/null; then
                      echo "MySQL is ready (after $i checks)"
                      break
                    fi
                    sleep 2
                  done
                '''
                               // (c) start the app (double quotes: image name from def vars)
                //     + self-healing: free host port 8090 if an OLD container
                //     still publishes it (bit us once: "port is already allocated")
                sh """
                  docker rm -f student-app 2>/dev/null || true
                  OLD_CONTAINERS=\$(docker ps -q --filter publish=8090)
                  if [ -n "\$OLD_CONTAINERS" ]; then
                    echo "Freeing host port 8090 (old containers: \$OLD_CONTAINERS)"
                    docker rm -f \$OLD_CONTAINERS
                  fi
                  docker run -d --name student-app --network appnet --restart unless-stopped -p 8090:8089 -e SPRING_DATASOURCE_URL='jdbc:mysql://mysql-db:3306/studentdb?createDatabaseIfNotExist=true' -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=root ${DOCKERHUB_USER.toLowerCase()}/${APP_NAME}:${BUILD_NUMBER}
                  docker ps | grep student-app
                """
                // (d) smoke test: any HTTP answer != 000 proves Tomcat is up
                //     (200 = API docs OK; 404/500 = app up, endpoint issue)
                sh '''
                  for i in $(seq 1 24); do
                    code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8090/student/v3/api-docs || true)
                    if [ "$code" != "000" ]; then
                      echo "App is UP - Tomcat answered HTTP $code"
                      exit 0
                    fi
                    sleep 5
                  done
                  echo "--- App not reachable after 2 minutes - last 60 log lines: ---"
                  docker logs --tail 60 student-app
                  exit 1
                '''
            }
        }

        // ==================================================================
        //  PLAN-AHEAD: DevSecOps stage 1 hooks (same as the demo pipeline -
        //  uncomment when the teacher introduces Phase 1, BEFORE stage 4).
        // ==================================================================
        // Secrets detection:
        //   stage('Scan Secrets (gitleaks)') {
        //       steps { sh 'docker run --rm -v $PWD:/repo zricethezav/gitleaks:latest detect -s /repo --no-git -v' }
        //   }
        //
        // Vulnerable dependencies (SCA):
        //   stage('OWASP Dependency-Check') {
        //       steps { sh 'mvn org.owasp:dependency-check-maven:check' }
        //   }
        //
        // Container image vulnerabilities (after docker.build, before push):
        //   stage('Trivy Image Scan') {
        //       steps { sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest image --exit-code 1 --severity CRITICAL ${DOCKERHUB_USER.toLowerCase()}/${APP_NAME}:${BUILD_NUMBER}" }
        //   }
    }

    post {
        success {
            echo 'SUCCESS: student-management deployed.'
            echo 'Smoke test: http://192.168.33.10:8090/student/v3/api-docs'
            echo 'Swagger UI: http://192.168.33.10:8090/student/swagger-ui/index.html'
        }
        failure {
            echo 'FAILED: open the red stage, then the classic Console Output for the full log.'
        }
        always {
            // Keep the workspace small: Jenkins should not hoard build junk
            cleanWs()
        }
    }
}
