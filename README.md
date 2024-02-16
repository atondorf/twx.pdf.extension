# Introduction 
This Thingworx Extension contains a Ressource to Print mashups as pdf.

This library is based on:
https://github.com/cbadici/thingworx-pdfgenerator-extension
But it uses playwright instead of jbrowserdriver, so it's working with OpenJDK and Thingworx 9.x

# Getting Started
As the playwright libraries are very huge they are not packed to the extension zip file.
You can manually upload them to your thingworx server, or uncomment the line in gradle file:

# Build
Place the ThingWorx SDK jar-files in the lib/common and call gradle.
If you want to inlude the huge "driver-bundle-1.31.0.jar" directly in the extension uncomment
"// packageDependencies group: 'com.microsoft.playwright', name: 'playwright', version: '1.31.0'" in "extension/build.gradle".

# Gradle Targets
The gradle script contains some special targets for the Extension.
- extPrepare:    Compiles the java files and prepares the extension structure 
- extZip:        Creates the extension zip-File

# Todos
- I would love to have a gradle target, that automatically uploads the extensio to a given test thingworx instance ... 

This Extension is provided as-is and without warranty or support.