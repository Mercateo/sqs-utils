# Release HowTo

1. Merge your changes into the master branch
1. Execute `mvn release:prepare`
    - prompts you for a tag, choose a SemVer conform tag that reflects the changes you made
        - take care of second prompt for SCM. by default it will prepend `sqs-utils-`. you have to set set this manually
        ```
        What is SCM release tag or label for "sqs-utils"? (com.mercateo.sqs:sqs-utils) sqs-utils-0.7.1: : 0.7.1
        ```
    - creates a new commit with the new tag
    - tags the new commit
    - creates another commit, which updates version to the next snapshot
    - commits everything to the Github repo
1. Wait for the [Github action](https://github.com/Mercateo/sqs-utils/actions/workflows/Deploy.yml) to deploy the artifact and for it to be available in Maven Central
    - the new version is available as soon as you find it in the maven repo: https://repo.maven.apache.org/maven2/com/mercateo/sqs/sqs-utils/

# deploy SNAPSHOT

* tag a commit and push this tag
* will be available here https://oss.sonatype.org/content/repositories/snapshots/com/mercateo/sqs/sqs-utils/
