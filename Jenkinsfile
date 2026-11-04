@Library('wf-ci') _

wfBuild(
    repo: 'WfLib',
    upstream: ['GemRender/main', 'Advanced-Hitbox-Framework/main', 'WarForge-Remaintained/multi-version'],
    jdk: '21',
    zig: true,
    branches: ['main'],
    build: 'build --no-daemon --stacktrace',
    publish: 'publish --no-daemon --stacktrace -Dorg.gradle.internal.http.socketTimeout=600000 -Dorg.gradle.internal.http.connectionTimeout=600000',
    version: [branches: ['main'], key: 'mod_version', jdk: '21', artifact: 'build/libs/wflib-@VERSION@.jar', reuseBuild: true],
    tagRolling: [
        tag: 'wf', jdk: '17', build: 'build -x test --no-daemon',
        stage: 'cp "$(find build/libs -maxdepth 1 -name \'wflib-*.jar\' ! -name \'*-sources.jar\' ! -name \'*-javadoc.jar\' | head -1)" wflib.jar',
        filesCmd: 'echo wflib.jar',
    ],
)
