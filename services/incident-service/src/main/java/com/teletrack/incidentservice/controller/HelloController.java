package com.teletrack.incidentservice.controller;

import com.teletrack.commonutils.dto.response.UserResponse;
import com.teletrack.incidentservice.client.UserServiceCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
public class HelloController {

    @GetMapping("/incidents/hello")
    public String hello(){
        System.out.println("********* "+ UserServiceCaller.validateUser(UUID.randomUUID()));
        System.out.println("********* "+ UserServiceCaller.validateUser(UUID.fromString("caa3b061-98db-44b9-8cd5-8b9ef8601f31")));

        Optional<UserResponse> user = UserServiceCaller.getUserById(UUID.fromString("caa3b061-98db-44b9-8cd5-8b9ef8601f31"));
        if(user.isPresent()){
            System.out.println(user.get().getFirstName());
            System.out.println(user.get().getRole());
        }else{
            System.out.println("no user found");
        }

        return "Hello From Incident Service !!";
    }
}
