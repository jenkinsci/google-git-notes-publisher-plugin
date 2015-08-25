Google Git Notes Plugin
====================

This plugin provides automatic recording of Jenkins build actions to Git Notes.

Usage
===
Requires the use of the Git Plugin. Go to the Configure page for any build that uses a Git SCM, and check the 'Log Build Status to Git Notes' box under Build Environment. This will trigger writes to Git Notes when the build starts and stops. Data is written in JSON format and includes the build URL and, if available, build result (success/failure).

Development
===========

How to build
--------------

	mvn clean verify

Creates the plugin HPI package for use with Jenkins.


License
-------

	(The Apache v2 License)

    Copyright 2015 Google Inc. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
