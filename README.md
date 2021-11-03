# MergeBot

[MergeBot_Config]: https://github.com/aposin/MergeBot_Config
[MergeBot_Config/config.json]: https://github.com/aposin/MergeBot_Config/blob/main/config.json

MergeBot is a tool for automatically merge changes to several branches using pull requests.
Furthermore it is able to execute Status checks on commits, detect self-reviews, link automatically created pull request with each other and execute post actions (after pull request merge).
The Groovy-Scripts and MergeBot Configuration-File can be found in the repository [MergeBot_Config][MergeBot_Config].
The configuration files must be adapted to your needs.
Furthermore you can manipulate the workflow by using specific labels.

* `rerun-check:` executes the status-checks again
* `manual:` opens up the next pull, cherry picks the changes but do not merge them automatically.  
* `no-porting:` the changes will not be merged automatically at all when closing your initial pull request.  

For further information please read the [user_manual.adoc].

## preparations on MergeBot
For testing and running the application you need a personal access token to the enterprise GitHub.  
You can generate one at your user settings. Go to your profile and choose Developer settings/Personal access tokens and click on "Generate new token", tick all the possible fields otherwise your token will not have all the levels of authorization it needs - save the token somewhere.   
Right now the token needs to be set into the code (marked as `//TODO`) because the application needs to authenticate at the enterprise GitHub.  
I am working on a solution for this, because I do not want to have the token as String in the code. 

The configuration files (repository: [MergeBot_Config][MergeBot_Config]) must be placed in the folder specified in [MergeBot_Config/config.json][MergeBot_Config/config.json]

## preparations on GitHub
Set up a Webhook in the repository where the MergeBot should be available. Then payloads are sent to the server where the MergeBot is running and to the specified port from config.json.
Example: http://your_server_name:8000
Select the following events for sending payloads:

* `Pull request`
* `Pull request reviews`

Make sure to disable all merge-buttons on pull requests except for: "Allow squash merging".
Otherwise the automated cherry pick for merging your changes automatically to several branches will not work.

## start application
To build a released version, checkout the version tag beforehand.
To build a executable shaded-jar, run on the root folder:
```bash
mvn package
```
Start it with 
```bash 
java -jar {shaded jar-file} {MergeBot Configuration-File example:config.json}
```
The parameter has to provide the path to the config.json file. Can be found in [MergeBot-config]

### Prerequisites

## Contributing

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for details on our code of conduct, and [CONTRIBUTING.md](CONTRIBUTING.md) for the process for submitting pull requests to us.

## Authors

The authors list is maintained in the [CONTRIBUTORS.txt](CONTRIBUTORS.txt) file.
See also the [Contributors](https://github.com/aposin/MergeBot/graphs/contributors) list at GitHub.

## License

This project is under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.  

## useful Links:  
### JavaAPI for GitHub:  
**GitHub Java API (org.eclipse.egit.github.core)**   
https://github.com/eclipse/egit-github/tree/master/org.eclipse.egit.github.core

**GitHub API for Java (org.kohsuke.github)**  
http://github-api.kohsuke.org/  

**GitHub API**  
https://developer.github.com/enterprise/2.15/v3/  

[MergeBot - config]: https://github.com/aposin/MergeBot_config
