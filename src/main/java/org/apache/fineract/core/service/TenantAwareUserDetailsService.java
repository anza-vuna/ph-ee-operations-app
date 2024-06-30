/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.core.service;

import org.apache.fineract.organisation.user.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.Collections;

import static org.apache.fineract.config.CacheConfig.CACHE_USER_BY_NAME;

@Component(value = "userDetailsService")
public class TenantAwareUserDetailsService implements UserDetailsService {

    @Autowired
    private AppUserRepository appUserRepository;

    @Override
    @Cacheable(cacheNames = CACHE_USER_BY_NAME)
    public UserDetails loadUserByUsername(final String username) {
        AppUser appUserByName = appUserRepository.findAppUserByName(username);
        if (appUserByName != null) {
            return appUserByName;
        } else {
            AppUser unknownUser = new AppUser();
            unknownUser.setRoles(Collections.emptySet());
            return unknownUser;
        }
    }
}
