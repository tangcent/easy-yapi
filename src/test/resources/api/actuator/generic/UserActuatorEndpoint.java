package com.itangcent.springboot.demo.controller.generic;

import com.itangcent.model.UserInfo;
import com.itangcent.springboot.demo.controller.generic.GenericEndpointBase;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Concrete Actuator endpoint binding the inherited `T` to [UserInfo].
 *
 * The declared operation is the one the scanner can see: `ActuatorEndpointScanner`
 * iterates `PsiClass#getMethods`, which does not include inherited members. The
 * inherited `read()` therefore only documents the unreachable generic case.
 */
@Endpoint(id = "generic")
public class UserActuatorEndpoint extends GenericEndpointBase<UserInfo> {

    /**
     * current item
     */
    @ReadOperation
    public UserInfo current() {
        return null;
    }
}
