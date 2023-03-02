# Introduction 
This Thingworx Extension contains some helpers for concurrent operations and synchronisation.
It's under development.

# Getting Started


# Build and Test
To build the extension, use gradle.
The gradle scripts expect the Thingworx Libraries to be available in a parallel folder named "_twx_libraries_9".

# Gradle Targets
The gradle script contains some special targets for the Extension.
- extVersion:    Increases the Version Number of the Extension by 0.0.1
- extPrepare:    Compiles the java files and prepares the extension structure 
- extZip:        Creates the extension zip-File
- extTwxDelete:  Deletes the extension in the connected Thingworx Instance
- extTwxInstall: Installs the extension in the connected Thingworx Instance

To make use of (de)installation using gradle, ensure you setup the parameters:
- thingworxServerRoot
- thingworxAppKey

# Contribute
TODO: Explain how other users and developers can contribute to make your code better. 

If you want to learn more about creating good readme files then refer the following [guidelines](https://docs.microsoft.com/en-us/azure/devops/repos/git/create-a-readme?view=azure-devops). You can also seek inspiration from the below readme files:
- [ASP.NET Core](https://github.com/aspnet/Home)
- [Visual Studio Code](https://github.com/Microsoft/vscode)
- [Chakra Core](https://github.com/Microsoft/ChakraCore)