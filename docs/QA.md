1. How to add custom config?

   * add .easy.api.config in the root of project or module
   
   * see [Supported-custom-rules.md](https://github.com/tangcent/easy-api/blob/master/docs/2.%20Supported-custom-rules.md)
   
2. How to group apis to special directory?

   * add config:
   
   ```properties
   #find module for comment tag 
   module=#module
   ```
   
   * add comment tag at class
   ```java
   /**
    * Mock Apis
    *
    * @module mock
    */
   @RestController
   @RequestMapping(value = "mock")
   public class MockCtrl {
   }
   ```

3. How to ignore apis?

   * add config:
   ```properties
   #ignore class or method which has comment tag 'ignore' 
   ignore=#ignore
   ```
   
   * add comment tag at controller class for ignore all apis in controller
   ```java
   /**
    * Mock Apis
    *
    * @ignore
    */
   @RestController
   @RequestMapping(value = "mock")
   public class MockCtrl {
   }
   ```
   
   * add comment tag at api for ignore special api in controller
   ```java
   /**
    * Mock Apis
    */
   @RestController
   @RequestMapping(value = "mock")
   public class MockCtrl {
   
        /**
         * Mock String
         * @ignore
         */
        @GetMapping("/string")
        public String mockString() {
            return Result.success("mock string");
        }

   }
   ```
   
4. How to set name&description of api/directory?
    * in general:
    ```java
    /**
     * The head line will be the name of api directory
     * The rest lines will be the description of api directory
     */
    @RestController
    @RequestMapping(value = "mock")
    public class MockCtrl {
    
        /**
         * The head line will be the name of api
         * The rest lines will be the description of api
         */
        @GetMapping("/string")
        public String mockString() {
            return Result.success("mock string");
        }
    }
    ```
    
5. How to mark api as deprecated in description
    * you can add additional config:
    ```properties
    doc.method[#deprecated]=groovy:"\n「deprecated」" + it.doc("deprecated")
    doc.method[@java.lang.Deprecated]=「deprecated」
    doc.method[@kotlin.Deprecated]=groovy:"\n「deprecated」" + it.ann("kotlin.Deprecated","message")

    doc.method[groovy:it.containingClass().hasDoc("deprecated")]=groovy:"\n「deprecated」" + it.containingClass().doc("deprecated")
    doc.method[groovy:it.containingClass().hasAnn("java.lang.Deprecated")]=「deprecated」
    doc.method[groovy:it.containingClass().hasAnn("kotlin.Deprecated")]=groovy:"\n「deprecated」 " + it.containingClass().ann("kotlin.Deprecated","message")

    ```
    
6. How to declare a api requires some special permission in a description with javax.annotation.security?

   
   * add config for spring security:
   ```properties
   # security description
   doc.method[@javax.annotation.security.RolesAllowed]=groovy:"\require role:"+it.ann("javax.annotation.security.RolesAllowed")
   ```
   * code:
   ```java
   /**
    * The head line will be the name of api directory
    * The rest lines will be the description of api directory
    */
   @RestController
   @RequestMapping(value = "mock")
   public class MockCtrl {
   
       /**
        * The head line will be the name of api
        * The rest lines will be the description of api
        */
       @GetMapping("/string")
       @RolesAllowed("admin")
       public String mockString() {
           return Result.success("mock string");
       }
   }

   ```
7. How to config for spring security

   * add config for spring security:
   ```properties
   # security description
   find_role_in_PreAuthorize=(function(exp){var str="";if(exp.indexOf("hasRole")!=-1){var roles=exp.match(/hasRole\\((.*?)\\)/);if(roles&&roles.length>1){str+="require role:"+roles[1];}};return str})
   doc.method[@org.springframework.security.access.prepost.PreAuthorize]=js:${find_role_in_PreAuthorize}(it.ann("org.springframework.security.access.prepost.PreAuthorize"))
   ```
   * code:
   ```java
   /**
    * The head line will be the name of api directory
    * The rest lines will be the description of api directory
    */
   @RestController
   @RequestMapping(value = "mock")
   public class MockCtrl {
   
       /**
        * The head line will be the name of api
        * The rest lines will be the description of api
        */
       @GetMapping("/string")
       @PreAuthorize("hasRole('admin')")
       public String mockString() {
           return Result.success("mock string");
       }
   }

   ```