package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;
    @GetMapping("/or/not/{id}")
    Result isFollowed(@PathVariable("id")Long id){
        return followService.isFollowed(id);
    }

    @PutMapping("/{id}/{isFollow}")
    Result followUser(@PathVariable("id")Long id,@PathVariable("isFollow")Boolean isFollow){
        return followService.followUser(id,isFollow);
    }

    @GetMapping("/common/{id}")
    Result commonFollowList(@PathVariable("id")Long id){
        return followService.commonFollowList(id);
    }
}
