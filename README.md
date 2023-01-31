Title: High-Fidelity Specular SVBRDF Acquisition from Flash Photographs

Author: Michael Tetzlaff

OS: Windows 10 (64 bit)

Hardware requirement: GTX 1050 Ti (4GB VRAM) or better

Instructions:
This project requires Java 11 (64 bit) and Maven in order to run.  If these prerequisites are already installed, skip steps 1-3.
All other dependencies will be installed through Maven.
1. 	Download and install OpenJDK 11: https://adoptium.net/temurin/releases/?version=11
	- Choose Windows, x64, .msi installer
2. 	Download and extract Maven 3.8.7: https://maven.apache.org/download.cgi
	- Choose either of the binary archives under the "Link" column under "Files."
	- Extract to C:\Program Files\apache-maven-3.8.7 (or another directory of your choice)
	- If it's installed correctly, the path C:\Program Files\apache-maven-3.8.7\bin should exist and contain a file called "mvn"
3. 	Add Maven to the PATH environment variable.
	- From the Start menu, search for "Edit environment variables for your account"
	- Double click "Path" under user variables
	- Click "New" and type "C:\Program Files\apache-maven-3.8.7" (or whatever directory it was installed in for step 2).
	- Click OK to close out both windows.
	- To confirm that Maven and Java are working, you can run the command "mvn --version" from a cmd.exe terminal.
4. 	Using a cmd.exe terminal or by simply double-clicking the file, execute the compile script: compile.bat
	An internet connection is required during this step to download dependencies.
5. 	Using a cmd.exe terminal or by simply double-clicking the file, execute the run script: run.bat
	You will see several GUI windows open; these can be ignored.  The program is scripted to automatically load and process the example data (included in the repository) and then terminate when processing is finished.

The output files will be in the "output" folder.  In particular, the file "reconstruction.png" corresponds to the representative figure provided.

The implementation of this research exists in a repository which contains code from other, somewhat unrelated research efforts.  The most important code for the project is in the following directories:
-	src/tetzlaff/ibrelight/export/specularfit
-	shaders/specularfit

The code in these directories do depend on a considerable amount of utility code from the rest of the repository; however, the novel research contributions of the work to be published in IEEE TVCG are for the most part in the directories listed above.
The entry point for the algorithm which is the focus of the published work is tetzlaff.ibrelight.export.specularfit.SpecularFitRequest.java.


README for original repository:

IBRelight

Contributors:
- Michael Tetzlaff (University of Minnesota / University of Wisconsin - Stout) - primary developer
- Alex Kautz (University of Rochester) - contributor
- Seth Berrier (University of Wisconsin - Stout) - contributor
- Michael Ludwig (University of Minnesota) - contributor
- Sam Steinkamp (University of Minnesota) - contributor
- Josh Lyu (University of Wisconsin - Stout) - contributor
- Mey Amor (University of Wisconsin - Stout) - contributor
- Gary Meyer (University of Minnesota) - designer and advisor

Copyright (c) Seth Berrier, Michael Tetzlaff, Zhangchi (Josh) Lyu, Mey Amor 2023
Copyright (c) The Regents of the University of Minnesota 2019

Licensed under GPLv3 
( http://www.gnu.org/licenses/gpl-3.0.html )

IBRelight is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
IBRelight is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 
Requests for source code, comments, or bug reports should be sent to
Michael Tetzlaff ( tetzlaffm@uwstout.edu )
