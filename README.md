# pipeline

Containes the code used by ellation Jenkins master pipelines.

## Code style

Code style verification is accomplished via [CodeNarc](http://codenarc.sourceforge.net) plugin
and is described in [CODESTYLE.md](./CODESTYLE.md).

To run code style checks locally following commands should be executed:
- `./gradlew codenarcMain` - runs CodenArc on `main` source set
- `./gradlew codenarcTest` - runs CodenArc on `test` source set
- `./gradlew codenarcIntegrationTest` - runs CodenArc on `integrationTest` source set
- `./gradlew codenarcAllSources` - runs CodenArc on all source sets

:bulb: Please pay attention that executing `codenarcAllSources` will execute tasks in a sequence
`codenarcMain` -> `codenarcTest` -> `codenarcIntegrationTest`
and if task fails with error (code style violation found), then next task won't be executed.

## Testing a PR
- [ ] Run the pipeline test-instance-pipeline-staging using the template code from template.rollback.groovy and the pipeline script + shared library from your branch
- [ ] Run the pipeline test-instance-pipeline-proto0 using the template code from template.regular.groovy and the pipeline script + shared library from your branch
