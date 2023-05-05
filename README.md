# Introduction 
This Thingworx Extension contains a Ressource to Print mashups as pdf.

This library is based on the idea of:
https://github.com/cbadici/thingworx-pdfgenerator-extension
But it uses playwright instead of jbrowserdriver, so it's working with OpenJDK and Thingworx 9.x

# Getting Started
As the playwright libraries are very huge they are not packed to the extension zip file.
You can manually upload them to your thingworx server, or uncomment the line in gradle file:
28:     // packageDependencies group: 'com.microsoft.playwright', name: 'playwright', version: '1.31.0'

# Build and Test
To build the extension, use gradle.

# Gradle Targets
The gradle script contains some special targets for the Extension.
- extPrepare:    Compiles the java files and prepares the extension structure 
- extZip:        Creates the extension zip-File

