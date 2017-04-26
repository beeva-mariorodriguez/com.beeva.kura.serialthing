# kura serial example

serial port with MQTT/Cloud integration example for eclipse kura

code from: 
  * [Eclipse Kura Serial Example](https://eclipse.github.io/kura/doc/serial-example.html)
  * [Eclipse Kura Heater Demo](https://github.com/eclipse/kura/tree/develop/kura/examples/org.eclipse.kura.demo.heater)


## instructions

1. install eclipse neon using Oomph installer, tested with two eclipse packages
  * Eclipse IDE for Java EE Developers
  * Eclipse IDE for Eclipse Committers
  * instructions for both [here](https://eclipse.github.io/kura/doc/kura-setup.html)
2. install mToolkit in eclipse from http://mtoolkit-neon.s3-website-us-east-1.amazonaws.com
3. import user space 
  * (kura)[https://www.eclipse.org/downloads/download.php?file=/kura/releases/2.1.0/user_workspace_archive_2.1.0.zip]
4. open target definition, click the link Set as Target Platform
5. clone repo in WS
6. right click on resources/serialthing.dpp, quick build
7. deploy resources/serialthing.dp to kura

## debug osgi using mtoolkit

in eclipse: window/show view/other, scroll to mtoolkit and chose Frameworks

add framework address: ``kura_device_IP`` port:``1450`` (TIP: use a ssh tunnel!)

## not working

* unable to build .dp file: problems with dependencies (javax.microedition.io)
* unable to build plugin if using ESF development environment: same problems as with .dp file

