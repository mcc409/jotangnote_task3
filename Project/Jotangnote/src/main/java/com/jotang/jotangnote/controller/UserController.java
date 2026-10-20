package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        userMapper.insert(user);
        user.setPassword(null);  // 返回时不带密码
        return user;
    }

    @PostMapping("/login")
    public User login(@RequestBody User user) {
        User found = userMapper.findByUsername(user.getUsername());
        if (found == null || !found.getPassword().equals(user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        found.setPassword(null);
        return found;
    }
}