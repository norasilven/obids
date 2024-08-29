# obids
This repository contains the code used in my Software Engineering Master Thesis research project: Towards On-Board Intrusion Detection for Small Satellites. This work was done in collaboration with, and is the intellectual property of the Netherlands Aerospace Centre (NLR). This README comtains instructions on how to integrate this code with the NanoSat MO Framework.

## NanoSat MO Framework
The European Space Agency's (ESA) NanoSat MO Framework can be found [here](https://github.com/esa/nanosat-mo-framework). First, make sure the correct versions of its prerequisites are installed, as stated in the README file. Then, follow the installation instructions in the `NMF Quickstart` section of the [documentation](https://nanosat-mo-framework.readthedocs.io/en/latest/quickstart.html).

## Integrating intrusion-detection-system and malware-attack
First, copy the `intrusion-detection-system` folder from this repository and paste it into the `nanosat-mo-framework/sdk/examples/space/` folder. Next, a couple of lines must be added to the project and build files.
* `nanosat-mo-framework/sdk/examples/space/pom.xml`: Add
  `<module>intrusion-detection-system</module>
   <module>malware-attack</module>`
* `nanosat-mo-framework/sdk/sdk-package/pom.xml`: Add
  `<dependency>
      <groupId>int.esa.nmf.sdk.examples.space</groupId>
      <artifactId>intrusion-detection-system</artifactId>
      <version>${project.version}</version>
   </dependency>`
   and
  `<dependency>
      <groupId>int.esa.nmf.sdk.examples.space</groupId>
      <artifactId>malware-attack</artifactId>
      <version>${project.version}</version>
    </dependency>`
   and
  `<copy todir="${esa.nmf.sdk.assembly.outputdir}/home/intrusion-detection-system">
		  <fileset dir="${basedir}/src/main/resources/space-common"/>
		  <fileset dir="${basedir}/src/main/resources/space-app-root"/>
   </copy>`
   and
  `<copy todir="${esa.nmf.sdk.assembly.outputdir}/home/malware-attack">
      <fileset dir="${basedir}/src/main/resources/space-common"/>
      <fileset dir="${basedir}/src/main/resources/space-app-root"/>
    </copy>`
* `nanosat-mo-framework/sdk/sdk-package/antpkg/build.xml`: Add
  `<target name="build"
    depends="emit-ctt, emit-cli-tool, emit-simulator-gui, emit-space-supervisor, emit-space-app-all-mc-services, emit-space-app-intrusion-detection-system,
     emit-space-app-adcs-eds, emit-space-app-publish-clock, emit-space-app-camera, emit-space-app-benchmark, emit-space-app-payloads-test, 
     emit-space-app-python-script, emit-space-app-malware-attack, emit-space-app-waveform, emit-space-app-camera-acquisitor-system, 
     emit-space-app-picture-processor, emit-space-app-space-to-space-supervisor, emit-space-app-mp-demo, emit-ground-app-mp-demo">`
  and
  `<target name="emit-space-app-intrusion-detection-system">
    <ant antfile="antpkg/build_shell_script.xml">
      <property name="mainClass" value="esa.mo.nmf.apps.IDS"/>
      <property name="id" value="start_intrusion-detection-system"/>
      <property name="binDir" value="intrusion-detection-system"/>
    </ant>
    <ant antfile="antpkg/build_batch_script.xml">
      <property name="mainClass" value="esa.mo.nmf.apps.IDS"/>
      <property name="id" value="start_intrusion-detection-system"/>
      <property name="binDir" value="intrusion-detection-system"/>
    </ant>
   </target>`
  and
  `<target name="emit-space-app-malware-attack">
    <ant antfile="antpkg/build_shell_script.xml">
      <property name="mainClass" value="esa.mo.nmf.apps.MalwareAttack"/>
      <property name="id" value="start_malware-attack"/>
      <property name="binDir" value="malware-attack"/>
    </ant>
    <ant antfile="antpkg/build_batch_script.xml">
      <property name="mainClass" value="esa.mo.nmf.apps.MalwareAttack"/>
      <property name="id" value="start_malware-attack"/>
      <property name="binDir" value="malware-attack"/>
    </ant>`

  
