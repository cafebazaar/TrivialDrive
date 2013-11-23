TrivialDrive
============

	TRIVIAL DRIVE - SAMPLE FOR IN-APP BILLING VERSION 3
	Copyright (c) 2012 Google Inc. All rights reserved.
	Bruno Oliveira, 2012-11-29
	Reza Mohammadi, 2013-11-23

CHANGELOG
---------

	2012-11-29: initial release
	2013-01-08: updated to include support for subscriptions
	2013-11-23: updating to include minor improvements in google's 5th release


WHAT IS THIS SAMPLE?
--------------------

   This game is a simple "driving" game where the player can buy gas
   and drive. The car has a tank which stores gas. When the player purchases
   gas, the tank fills up (1/4 tank at a time). When the player drives, the gas
   in the tank diminishes (also 1/4 tank at a time).

   The user can also purchase a "premium upgrade" that gives them a red car
   instead of the standard blue one (exciting!).

   The user can also purchase a subscription ("infinite gas") that allows them
   to drive without using up any gas while that subscription is active.


HOW TO RUN THIS SAMPLE
----------------------

   This sample can't be run as-is. You have to create your own
   application instance in the Developer Console and modify this
   sample to point to it. Here is what you must do:

### IN THE CODE

1. Change the sample's package name to your package name. To do that, you only need 
to update the package name in `AndroidManifest.xml` and correct the references (especially
the references to the R object).

2. Make sure that `AndroidManifest.xml` lists the updated package name! It's also
important that the `AndroidManifest.xml` file also includes the in-app permission:

    <uses-permission android:name="com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR" />

3. Export an APK, signing it with your PRODUCTION (not debug) developer certificate


### ON THE GOOGLE PLAY DEVELOPER CONSOLE
   
1. Upload the application to the [Developer Panel](panel).

[Developer Panel]: http://cafebazaar.ir/panel/

2. Using the **Enter** button in In-app Billing column of the created app,
go to In-app Billing Panel.

3. In that app, create in-app items with these IDs:
	`premium`, `gas`
Set their prices to 1000 rials (or any other price you like,
but make it a small price since this is just for testing purposes).

4. In that app, create a SUBSCRIPTION items with this ID:
	`infinite_gas`
Set the price to 1000 rials and the billing recurrence to montly. Just so
you are not immediately charged when you test it, set the trial period to
seven days.

5. Grab the application's public key (a base-64 string) -- you will need
that next. Note that this is the *application's* public key, not the
developer public key. You can find the application's public key in
the **Dealer Apps** page for your application.

### BACK TO THE CODE

1. Open MainActivity.java and replace the placeholder key with your app's public key.

2. Increase `versionCode` in `AndroidManifest.xml`.

3. Export an APK, signing it with your PRODUCTION (not debug) developer certificate

### BACK TO GOOGLE PLAY DEVELOPER CONSOLE
   
1. Upload the updated APK to Google Play
    
### TEST THE CODE

1. Install the APK, signed with your PRODUCTION certificate, to a test device [*] 
2. Run the app
3. Make purchases (make sure you're purchasing with an account that's NOT
  your developer account with which you uploaded the app to Google Play).

   Remember to refund any real purchases you make, if you don't want the 
   charges to actually to through.

   [*]: it will be easier to use a test device that doesn't have your
   developer account logged in; this is because, if you attempt to purchase
   an in-app item using the same account that you used to publish the app,
   the purchase will not go through.


A NOTE ABOUT SECURITY
---------------------

   This sample app implements signature verification but does not demonstrate
   how to enforce a tight security model. When releasing a production application 
   to the general public, we highly recommend that you implement the security best
   practices described in our documentation at:

   [http://pardakht.cafebazaar.ir/doc/security-design/?l=en]

   In particular, you should set developer payload strings when making purchase
   requests and you should verify them when reading back the results. This will make
   it more for a malicious party to perform a replay attack on your app.
