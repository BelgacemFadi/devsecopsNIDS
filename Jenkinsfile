
// ==========================================================================
// DevSecOps CI/CD Pipeline - student-management (devopsNIDS repo)
// ESPRIT 5th year - same validated 7-stage pattern as the demo pipeline:
//   Git -> Jenkins -> SonarQube (SAST + quality gate) -> Nexus (artifacts)
//     -> Docker Hub (images) -> Deploy + smoke test on the VM
//   + ACCEPTANCE PHASE (Lab 3): stage 8 runs gauntlt attacks from the repo
//   + PRODUCTION PHASE: stages 9-11 (approval gate -> prod deploy -> smoke)
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
//   4) gauntlt installed on the VM:
//      sudo apt install -y ruby ruby-dev build-essential nmap curl
//      sudo gem install gauntlt --no-document
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
        // STAGE 1b - Secret detection (DevSecOps Phase 1: Lab 1 + gitleaks).
        // Started from the PLAN-AHEAD sketch (bottom of this file) and
        // hardened it:
        //   --no-git       scan THIS build's working tree, not git history
        //   --redact       mask secret values in the Jenkins console output
        //   --exit-code 1  fail the stage when leaks are found (already the
        //                  default - kept explicit so the policy is visible)
        //   --report-path  JSON report archived in the post block below
        //   --user         run as the jenkins uid so the report is not left
        //                  root-owned inside the workspace
        // Quoting: SINGLE quotes - only bash $(...) inside, no Groovy def vars.
        // First run on an old repo? Temporarily use --exit-code 0 to see the
        // findings without failing, review, then flip back to --exit-code 1.
        // ------------------------------------------------------------------
        stage('1b. Scan Secrets (gitleaks)') {
            steps {
                sh '''
                  docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/repo" -w /repo \
                    zricethezav/gitleaks:latest detect --source /repo --no-git \
                    --redact --verbose --exit-code 1 \
                    --report-path /repo/gitleaks-report.json
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'gitleaks-report.json',
                                     allowEmptyArchive: true
                }
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
        // STAGE 2b - Lab 2: Retire.js SCA (JavaScript dependencies).
        // This repo is pure Java (no package.json), so `when` SKIPS the
        // stage today - the gate EXISTS and arms itself the day any JS code
        // (frontend, node tooling) enters the repo.
        // Lab 2 evidence is produced manually on the Angular test project
        // (see lab walkthrough). retire exits with code 13 when it finds
        // vulnerable libraries; --exitwith 1 makes that explicit.
        // Quoting: SINGLE quotes - no Groovy def vars inside.
        // ------------------------------------------------------------------
        stage('2b. Retire.js SCA (JS dependencies)') {
            when { expression { return fileExists('package.json') } }
            steps {
                sh '''
                  docker run --rm -v "$PWD:/src" -w /src node:20-alpine \
                    sh -c "npm install --ignore-scripts --no-audit --no-fund && npm install -g retire && retire --path . --exitwith 1"
                '''
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
                  docker run -d --env-file /var/lib/jenkins/db.env --name student-app --network appnet --restart unless-stopped -p 8090:8089 ${DOCKERHUB_USER.toLowerCase()}/${APP_NAME}:${BUILD_NUMBER}
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

        // ------------------------------------------------------------------
        // STAGE 8 - ACCEPTANCE PHASE (Lab 3): gauntlt attacks.
        // The attacks live IN THE REPO (attacks/*.attack) - security
        // acceptance criteria are versioned with the code they test.
        // Bare `gauntlt` globs ./attacks/*.attack from the workspace and
        // tests the DEPLOYED app (staging, port 8090) because stage 7 just
        // proved it is up. gauntlt runs cucumber with --strict: any failed
        // scenario exits non-zero and FAILS the build -> a real gate.
        // Attacks: curl (business API 200 + Swagger UI 200),
        //          nmap (only the ports we publish are open).
        // ------------------------------------------------------------------
        stage('8. Acceptance Tests (gauntlt)') {
            steps {
                sh 'gauntlt'
            }
        }

        // ------------------------------------------------------------------
        // STAGE 9 - PRODUCTION PHASE: manual approval gate.
        // A human decides promotion. Only builds that passed EVERY earlier
        // gate (secrets, SCA, SAST, quality, artifact, image, deploy,
        // acceptance) even REACH this prompt. In Jenkins, `input` pauses
        // the pipeline until someone clicks Deploy to Production.
        // ------------------------------------------------------------------
        stage('9. Production Approval (manual gate)') {
            steps {
                input message: 'Promote build to PRODUCTION?', ok: 'Deploy to Production'
            }
        }

        // ------------------------------------------------------------------
        // STAGE 10 - PRODUCTION PHASE: promote the SAME artifact.
        // The image tag is the SAME ${BUILD_NUMBER} that passed acceptance
        // - promotion, never rebuilding. Only the container name and host
        // port differ (prod = 8091, staging = 8090). Same mysql-db sidecar
        // over appnet, same SPRING_DATASOURCE_* env (copied from stage 7).
        // Quoting: DOUBLE quotes - Groovy def vars + BUILD_NUMBER inside,
        // bash vars escaped as \$ (same layout as stage 7c).
        // ------------------------------------------------------------------
        stage('10. Deploy to Production') {
            steps {
                sh """
                  docker network create appnet 2>/dev/null || true
                  docker rm -f student-app-prod 2>/dev/null || true
                  OLD_PROD=\$(docker ps -q --filter publish=8091)
                  if [ -n "\$OLD_PROD" ]; then
                    echo "Freeing host port 8091 (old containers: \$OLD_PROD)"
                    docker rm -f \$OLD_PROD
                  fi
                  docker run -d --env-file /var/lib/jenkins/db.env --name student-app-prod --network appnet --restart unless-stopped -p 8091:8089 ${DOCKERHUB_USER.toLowerCase()}/${APP_NAME}:${BUILD_NUMBER}
                  docker ps | grep student-app-prod
                """
            }
        }

        // ------------------------------------------------------------------
        // STAGE 11 - PRODUCTION PHASE: smoke test.
        // Prod must answer HTTP 200 on the BUSINESS API (not just any HTTP
        // answer like stage 7 - production is the stricter environment).
        // Retry loop: the JVM needs ~15s to boot (13.9s measured).
        // Quoting: SINGLE quotes - pure bash, no Groovy vars.
        // ------------------------------------------------------------------
        stage('11. Production Smoke Test') {
            steps {
                sh '''
                  for i in $(seq 1 12); do
                    code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/student/students/getAllStudents || true)
                    echo "Prod attempt $i -> $code"
                    if [ "$code" = "200" ]; then
                      echo "Production is UP - HTTP 200"
                      exit 0
                    fi
                    sleep 5
                  done
                  echo "--- Production not reachable after 1 minute - last 60 log lines: ---"
                  docker logs --tail 60 student-app-prod
                  exit 1
                '''
            }
        }
        stage('12. Configuration Safety Check') {
            steps {
                sh '''
                  echo "=== Configuration Safety Check (Slide 8) ==="
                  test "$(docker inspect -f '{{.State.Running}}' student-app-prod)" = "true"
                  echo "[OK] prod container is running"

                  docker inspect -f '{{json .HostConfig.PortBindings}}' student-app-prod | grep -q '"HostPort":"8091"'
                  echo "[OK] port binding is host 8091 -> container 8089"

                  docker inspect -f '{{json .NetworkSettings.Networks}}' student-app-prod | grep -q '"appnet"'
                  echo "[OK] prod container is on the appnet network"

                  test "$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' student-app-prod)" = "unless-stopped"
                  echo "[OK] restart policy is unless-stopped"

                  test "$(docker inspect -f '{{.Config.Image}}' student-app-prod)" = "$(docker inspect -f '{{.Config.Image}}' student-app)"
                  echo "[OK] prod runs the SAME image as staging (promotion, not rebuild)"

                  docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' student-app-prod | grep -q 'SPRING_DATASOURCE_URL=jdbc:mysql://mysql-db:3306'
                  echo "[OK] DB config was delivered from the protected db.env file"
                  echo "=== Configuration Safety Check: PASSED ==="
                '''
            }
        }

        // ==================================================================
        //  PLAN-AHEAD: DevSecOps Phase 1 (remaining NEXT STEPS).
        //  [x] Secrets detection    -> IMPLEMENTED: stage '1b. Scan Secrets
        //      (gitleaks)' (uncommented + hardened from this sketch)
        //  [x] Acceptance phase     -> IMPLEMENTED: stage '8. Acceptance
        //      Tests (gauntlt)' (Lab 3: attacks/*.attack versioned in repo)
        //  [x] Production phase     -> IMPLEMENTED: stages 9-11 (input gate,
        //      promote SAME image to student-app-prod:8091, smoke test 200)
        //  [ ] Vulnerable Java deps -> NEXT STEP 2: OWASP Dependency-Check
        //      (free NVD API key needed; will flag springdoc 2.1.0 -> 2.8.9)
        //  [ ] Container images     -> NEXT STEP 3: Trivy (after docker.build,
        //      before push) + one-off Docker Bench
        // ==================================================================
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
            echo 'SUCCESS: student-management deployed + acceptance-tested.'
            echo 'Staging  : http://192.168.33.10:8090/student/v3/api-docs'
            echo 'Swagger  : http://192.168.33.10:8090/student/swagger-ui/index.html'
            echo 'Prod API : http://192.168.33.10:8091/student/students/getAllStudents'
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
