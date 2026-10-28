package com.smartfix.communityapp;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional diagnostics identify the actual serving JVM, never a client-supplied label. */
@Component
public class InstanceIdentity implements Filter {
    private final String owner; private final String release;
    public InstanceIdentity(@Value("${smartfix.community.instance-id:community}")String id)throws IOException{
        owner=id+":"+ProcessHandle.current().pid();
        try(var input=getClass().getResourceAsStream("/community-release.txt")){
            release=input==null?"stage8-r1":new String(input.readAllBytes(),StandardCharsets.UTF_8).trim();
        }
    }
    public String owner(){return owner;}
    public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        var http=(HttpServletResponse)response;http.setHeader("X-Community-Instance",owner);http.setHeader("X-Community-Release",release);chain.doFilter(request,response);
    }
}
