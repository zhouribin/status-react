def slackNotify(Map config) {
  slackSend(
    color: config.get('color', 'good'),
    channel: '#jenkins-desktop',
    message: "${BRANCH_NAME}(${env.CHANGE_BRANCH}) ${config.message} ${env.BUILD_URL}"
  )
}

def doGitRebase(Map config) {
  try {
    sh "git rebase ${config.branch}"
  } catch (e) {
    sh 'git rebase --abort'
    throw e
  }
}

def installJSDeps() {
  def attempt = 1
  def maxAttempts = 10
  def installed = false
  sh 'node -v'
  sh 'npm -v'
  while (!installed && attempt <= maxAttempts) {
    println "#${attempt} attempt to install npm deps"
    sh 'scripts/prepare-for-platform.sh desktop'
    sh 'npm install'
    installed = fileExists('node_modules/web3/index.js')
    attemp = attempt + 1
  }
}

def cleanupBuild() {
  sh 'rm -rf node_modules'
  sh "rm -rf ${env.PKG_DIR}"
  sh 'rm -rf desktop/modules'
  sh 'rm -rf desktop/node_modules'
}

def cleanupAndDeps() {
  cleanupBuild
  sh 'cp .env.jenkins .env'
  sh 'lein deps'
  installJSDeps
}

def buildClosureScript() {
  sh 'rm -f index.desktop.js'
  sh 'lein prod-build-desktop'
  sh "mkdir ${env.PKG_DIR}"
  sh """
    react-native bundle \\
      --entry-file index.desktop.js \\
      --dev false --platform desktop \\
      --bundle-output ${env.PKG_DIR}/StatusIm.jsbundle \\
      --assets-dest ${env.PKG_DIR}/assets
  """
}

def buildLinux(Map config) {
  /* add path for QT installation binaries */
  env.PATH = "${config.qt_bin}:${env.PATH}"
  dir('desktop') {
    sh 'rm -rf CMakeFiles CMakeCache.txt cmake_install.cmake Makefile'
    sh """
      cmake -Wno-dev \\
        -DCMAKE_BUILD_TYPE=Release \\
        -DEXTERNAL_MODULES_DIR='${config.external_modules_dir.join(";")}' \\
        -DJS_BUNDLE_PATH='${workspace}/${env.PKG_DIR}/StatusIm.jsbundle' \\
        -DCMAKE_CXX_FLAGS:='-DBUILD_FOR_BUNDLE=1'
    """
    sh 'make'
  }
}

def buildMacOS(Map config) {
  /* add path for QT installation binaries */
  env.PATH = "/Users/administrator/qt/5.9.1/clang_64/bin:${env.PATH}"
  dir('desktop') {
    sh 'rm -rf CMakeFiles CMakeCache.txt cmake_install.cmake Makefile'
    sh """
      cmake -Wno-dev \\
        -DCMAKE_BUILD_TYPE=Release \\
        -DEXTERNAL_MODULES_DIR='${config.external_modules_dir.join(";")}' \\
        -DJS_BUNDLE_PATH='${env.PKG_DIR}/StatusIm.jsbundle' \\
        -DCMAKE_CXX_FLAGS:='-DBUILD_FOR_BUNDLE=1'
    """
    sh 'make'
  }
}

def createMacOSbundle() {
  dir(env.PKG_DIR) {
    sh 'git clone https://github.com/vkjr/StatusAppFiles.git 
    sh 'unzip StatusAppFiles/StatusIm.app.zip'
    sh 'cp -r assets/share/assets StatusIm.app/Contents/MacOs'
    sh 'chmod +x StatusIm.app/Contents/MacOs/ubuntu-server'
    sh 'cp ../desktop/bin/StatusIm StatusIm.app/Contents/MacOs'
    sh """
      macdeployqt StatusIm.app -verbose=1 -dmg \\
        -qmldir='${workspace}/node_modules/react-native/ReactQt/runtime/src/qml/'
    """
    sh 'rm -f StatusAppFiles'
  }
}
