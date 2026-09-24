package com.smartfix.auth.service;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.service.UserService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SmartFixUserDetailsService implements UserDetailsService {
    private final UserService userService;

    public SmartFixUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public SmartFixUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            return new SmartFixUserDetails(userService.findAuthenticationByUsername(username));
        } catch (ResourceNotFoundException exception) {
            throw new UsernameNotFoundException("Invalid username or password.");
        }
    }
}
