/*
 * Copyright 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.crm.order

import javax.servlet.http.HttpServletRequest
import java.security.MessageDigest

/**
 * Dummy security service that is used by integration tests.
 *
 * @author Goran Ehrsson
 * @since 1.0
 */
class TestSecurityService {
    def tenant = [id: 1L, name: "Default Tenant", user: [username: "nobody", email: "nobody@unknown.net"],
            options: [], dateCreated: new Date(), expires: (new Date() + 10)]
    def user = [guid: "576793b8-106d-4d60-bb26-e953c874d501", username: "nobody", name: "Nobody", email: "nobody@unknown.net",
            enabled: true, timezone: TimeZone.getDefault(), roles: [], permissions: []]

    /**
     * Execute a piece of code as a specific user.
     * @param username
     * @param closure
     * @return
     */
    def runAs(String username, Closure closure) {
        def restore = user.username
        try {
            user.username = username
            closure.call()
        } finally {
            user.username = restore
        }
    }

    /**
     * Return information about the current executing user.
     * @return
     */
    Map<String, Object> getCurrentUser() {
        user
    }

    /**
     * Return information about any user.
     * @param username
     * @return
     */
    Map<String, Object> getUserInfo(String username) {
        return getCurrentUser()
    }

    Map<String, Object> getCurrentTenant() {
        tenant
    }

    Map<String, Object> getTenantInfo(Long tenant) {
        getCurrentTenant()
    }
    /**
     * Get a list of all tenant that a user owns.
     * @param username username
     * @return
     */
    List<Map<String, Object>> getTenants(String username) {
        [tenant]
    }

    /**
     * Check if a user has permission to acces a tenant.
     *
     * @param tenantId the tenant ID to check
     * @param username username or null for current user
     * @return
     */
    boolean isValidTenant(Long tenantId, String username = null) {
        true
    }

    /**
     * Checks if the current user is authenticated in this session.
     * @return
     */
    boolean isAuthenticated() {
        true
    }

    /**
     * Checks if the current user has permission to perform an operation.
     * @param permission
     * @return
     */
    boolean isPermitted(Object permission) {
        true
    }
    /**
     * Alert system about a possible security breach.
     * @param request the offending request
     * @param topic topic (for example: 'accessDenied')
     * @param message informative message
     */
    void alert(HttpServletRequest request, String topic, String message) {
        println message
    }

    private static final int hashIterations = 1000

    def hashPassword(String password, byte[] salt) {
        //password.encodeAsSHA256()
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset()
        digest.update(salt)
        byte[] input = digest.digest(password.getBytes("UTF-8"))
        for (int i = 0; i < hashIterations; i++) {
            digest.reset()
            input = digest.digest(input)
        }
        return input.encodeHex().toString()
    }

    byte[] generateSalt() {
        byte[] buf = new byte[128]
        new Random(System.currentTimeMillis()).nextBytes(buf)
        return buf
    }

}
