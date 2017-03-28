# kura serial example

serial port example code from [Eclipse Kura Serial Example](https://eclipse.github.io/kura/doc/serial-example.html|https://eclipse.github.io/kura/doc/serial-example.html)

_not working!_

## instructions

1. install eclipse neon using Oomph installer, tested with two eclipse packages
  * Eclipse IDE for Java EE Developers
  * Eclipse IDE for Eclipse Committers
  * instructions for both [here](https://eclipse.github.io/kura/doc/kura-setup.html)
2. install mToolkit in eclipse from http://mtoolkit-neon.s3-website-us-east-1.amazonaws.com
3. import user space 
  * (ESF)[http://esf.eurotech.com/]
  * (kura)[https://www.eclipse.org/downloads/download.php?file=/kura/releases/2.1.0/user_workspace_archive_2.1.0.zip]
4. open target definition, click the link Set as Target Platform
5. clone repo in WS
6. right click on src/main/dp/serialthing.dpp, quick build
7. deploy bin/serialthing.dp to kura

## debug osgi using mtoolkit

in eclipse: window/show view/other, scroll to mtoolkit and chose Frameworks

add framework address: ``kura_device_IP`` port:``1450`` (TIP: use a ssh tunnel!)

## not working

deployed app does not work:
```
!STACK 0
java.lang.ClassNotFoundException: com.beeva.kura.serialthing.SerialThing
	at org.eclipse.osgi.internal.loader.BundleLoader.findClassInternal(BundleLoader.java:501)
	at org.eclipse.osgi.internal.loader.BundleLoader.findClass(BundleLoader.java:421)
	at org.eclipse.osgi.internal.loader.BundleLoader.findClass(BundleLoader.java:412)
	at org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader.loadClass(DefaultClassLoader.java:107)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
	at org.eclipse.osgi.internal.loader.BundleLoader.loadClass(BundleLoader.java:340)
	at org.eclipse.osgi.framework.internal.core.BundleHost.loadClass(BundleHost.java:229)
	at org.eclipse.osgi.framework.internal.core.AbstractBundle.loadClass(AbstractBundle.java:1212)
	at org.eclipse.equinox.internal.ds.model.ServiceComponent.createInstance(ServiceComponent.java:493)
	at org.eclipse.equinox.internal.ds.model.ServiceComponentProp.createInstance(ServiceComponentProp.java:272)
	at org.eclipse.equinox.internal.ds.model.ServiceComponentProp.build(ServiceComponentProp.java:333)
	at org.eclipse.equinox.internal.ds.InstanceProcess.buildComponent(InstanceProcess.java:620)
	at org.eclipse.equinox.internal.ds.InstanceProcess.buildComponents(InstanceProcess.java:197)
	at org.eclipse.equinox.internal.ds.Resolver.buildNewlySatisfied(Resolver.java:473)
	at org.eclipse.equinox.internal.ds.Resolver.enableComponents(Resolver.java:217)
	at org.eclipse.equinox.internal.ds.SCRManager.performWork(SCRManager.java:816)
	at org.eclipse.equinox.internal.ds.SCRManager$QueuedJob.dispatch(SCRManager.java:783)
	at org.eclipse.equinox.internal.ds.WorkThread.run(WorkThread.java:89)
	at org.eclipse.equinox.internal.util.impl.tpt.threadpool.Executor.run(Executor.java:70)
```
```
osgi> ls
(...)
47	Unsatisfied		com.beeva.kura.serialthing.SerialThing			com.beeva.kura.serialthing(bid=86)

osgi> component 47
	Component[
	name = com.beeva.kura.serialthing.SerialThing
	activate = activate
	deactivate = deactivate
	modified = updated
	configuration-policy = require
	factory = null
	autoenable = true
	immediate = true
	implementation = com.beeva.kura.serialthing.SerialThing
	state = Unsatisfied
	properties = {service.pid=com.beeva.kura.serialthing.SerialThing}
	serviceFactory = false
	serviceInterface = [com.beeva.kura.serialthing.SerialThing]
	references = {
		Reference[name = ConnectionFactory, interface = org.osgi.service.io.ConnectionFactory, policy = static, cardinality = 1..1, target = null, bind = setConnectionFactory, unbind = unsetConnectionFactory]
	}
	located in bundle = com.beeva.kura.serialthing_0.3.0 [86]
]
Dynamic information :
  The component is satisfied
  All component references are satisfied
  Component configurations :
    Configuration properties:
      component.name = com.beeva.kura.serialthing.SerialThing
      serial.baudrate = 9600
      objectClass = String[com.beeva.kura.serialthing.SerialThing]
      serial.parity = none
      kura.service.pid = com.beeva.kura.serialthing.SerialThing
      serial.stop-bits = 1
      service.pid = com.beeva.kura.serialthing.SerialThing
      serial.data-bits = 8
      component.id = 84
    Instances:

```



