---
name: Bug report
about: Create a report to help us improve
title: ''
labels: ''
assignees: ''

---

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Desktop (please complete the following information):**
 - OS: [e.g. iOS]
 - Browser [e.g. chrome, safari]
 - Version [e.g. 22]

1. Add config:
   
   ```properties
   http.call.before=groovy:logger.info("call:"+request.url())
   http.call.after=groovy:logger.info("response:"+response.string())
   api.class.parse.before=groovy:logger.info("[api] before parse class:"+it)
   api.class.parse.after=groovy:logger.info("[api] after parse class:"+it)
   api.method.parse.before=groovy:logger.info("[api] before parse method:"+it)
   api.method.parse.after=groovy:logger.info("[api] after parse method:"+it)
   api.param.parse.before=groovy:logger.info("[api] before parse param:"+it)
   api.param.parse.after=groovy:logger.info("[api] after parse param:"+it)
   json.class.parse.before=groovy:logger.info("[json] before parse class:"+it)
   json.class.parse.after=groovy:logger.info("[json] after parse class:"+it)
   json.method.parse.before=groovy:logger.info("[json] before parse method:"+it)
   json.method.parse.after=groovy:logger.info("[json] after parse method:"+it)
   json.field.parse.before=groovy:logger.info("[json] before parse field:"+it)
   json.field.parse.after=groovy:logger.info("[json] after parse field:"+it)
   ```

2. Console output `easy_api`
   - Please set <kbd>Preferences(Settings)</kbd> > <kbd>Other Settings</kbd> > <kbd>EasyApi</kbd> > <kbd> Common</kbd> > <kbd>log</kbd> to `VERBOSE```
   
3. Logs of IDEA: <br>
   The easiest way to find the product log file is the Help menu, the item name would depend on the IDE version and OS:- Show Log in Explorer
   - Show Log in Finder
   - Show Log in Konqueror/Nautilus
   - Reveal Log in Explorer
   - Reveal Log in Finder/Finder/Nautilus

**Additional context**
Add any other context about the problem here.
