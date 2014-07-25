Tor_Onion_Proxy_Library
=======================
NOTE: This project exists independently of the Tor Project.

__What__: Enable Android and Java applications to easily host their own Tor Onion Proxies using the core Tor binaries. Just by including an AAR or JAR an app can launch and manage the Tor OP as well as start a hidden service.

__Why__: It's sort of a pain to deploy and manage the Tor OP, we want to make it much easier.

__How__: We are really just a thin Java wrapper around the Tor OP binaries and jtorctl. 

__Who__: This work is part of the [Thali Project](http://www.thaliproject.org/mediawiki/index.php?title=Main_Page) and is being actively developed by Yaron Y. Goland assigned to the Microsoft Open Technologies Hub. We absolutely need your help! Please see the FAQ below if you would like to help!

To use the library clone the repo and run first run './gradlew uploadArchives' (make sure you have local maven installed) on Universal. Then run './gradlew build' on either the Android or Java project depending on your needs. The Android project contains an ARM binary. The Java project contains binaries for Linux, Mac and Windows.

The main class of interest is the OnionProxyManager. You can create this on Android using the AndroidOnionProxyManager and in Java using the (no points for guessing) JavaOnionProxyManager. See the OnionProxyManager class in universal for details on supported methods and such.

# Acknowledgements
A huge thanks to Michael Rogers and the Briar project. This project started by literally copying their code (yes, I asked first) which handled things in Android and then expanding it to deal with Java. We are also using Briar's fork of JTorCtl until their patches are accepted by the Guardian Project.

Another huge thanks to the Guardian folks for both writing JTorCtl and doing the voodoo to get the Tor OP running on Android.

And of course an endless amount of gratitude to the heroes of the Tor project for making this all possible in the first place and for their binaries which we are using for all our supported Java platforms.

# FAQ
## What's the relationship between universal, Java and android projects?
The universal project produces a JAR that contains code that is common to both the Java and Android versions of the project. We need this JAR available separately because we use this code to build other projects that also share code between Java and Android. So universal is very useful because we can include universal into our project's 'common' code project without getting into any Java or Android specific details. 

On top of universal are the java and android projects. They contain code specific to those platforms along with collateral like binaries.

Note however that shared files like the jtorctl-briar, geoip and torrc are kept in Universal and we use a gradle task to copy them into the android and java projects.

One further complication are tests. Hard experience has taught that putting tests into universal doesn't work well because it means we have to write custom wrappers for each test in android and java in order to run them. So instead the tests live primarily in the android project and we use a gradle task to copy them over to the Java project. This lets us share identical tests but it means that all edits to tests have to happen in the android project. Any changes made to shared test code in the java project will be lost. This should not be an issue for anyone but a dev actually working on Tor_Onion_Proxy_Library, to users its irrelevant.
## What is the maturity of the code in this project?
Well the release version is currently 0.0.0 so that should say something. This is an alpha. We have (literally) one test. Obviously we need a heck of a lot more coverage. But we have run that test and it does actually work which means that the Tor OP is being run and is available.
## Can I run multiple programs next to each other that use this library?
Yes, they won't interfere with each other. We use dynamic ports for both the control and socks channel. 
## Can I help with the project?
ABSOLUTELY! You will need to sign a [Contributor License Agreement](https://cla.msopentech.com/) before submitting your pull request. To complete the Contributor License Agreement (CLA), you will need to submit a request via the form and then electronically sign the Contributor License Agreement when you receive the email containing the link to the document. This needs to only be done once for any Microsoft Open Technologies OSS project. 

Please make sure to configure git with a username and email address to use for your commits. Your username should be your GitHub username, so that people will be able to relate your commits to you. From a command prompt, run the following commands:
```
git config user.name YourGitHubUserName
git config user.email YourAlias@YourDomain
```

What we most need help with right now is test coverage. But we also have a bunch of features we would like to add. See our issues for a list.
## Where does jtorctl-briar.jar come from?
This is a fork of jtorctl with some fixes from Briar. So we got it out of Briar's depot. The plan is that jtorctl is supposed to accept Briar's changes and then we will start to build jtorctl ourselves from the Tor depot directly.
## Where did the binaries for the Tor OP come from?
I grabbed the ARM binary from Briar's depot but I believe they got it from Guardian. We really need to start building that ourselves.

I grabbed the Windows tor.exe file from Tor's expert bundle. I had to install it and then went into the install files, found tor.exe and extracted it.
I grabbed the OS/X and Linux (both 32 and 64 bit) from the Tor Browser Bundles for those platforms. Note that the Mac version consists of the tor.real and libevent-2.0.5.dylib files. The Linux versions consists of the tor and libevent-2.0.so.5 files.

And yes, we really should just build this all ourselves.
## Where did the geoip file come from?
I'm pretty sure it came from the Windows Expert Bundle.
