# Release HowTo
1. Merge your changes into the master branch
1. Tag the merge commit that you want to release
1. Update the SNAPSHOT version in the pom and push it
    - commit message should be something like `Update version in pom for next development iteration`
1. push everything with `git push --tags origin master`
