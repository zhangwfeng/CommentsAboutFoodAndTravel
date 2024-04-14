package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private IFollowService followService;

    /**
     *用户关注与取关
     * @param id
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable Long id,@PathVariable Boolean isFollow){
        return followService.follow(id,isFollow);
    }

    /**
     * 查询用户是否关注
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable Long id){
        return followService.isFollow(id);
    }

    /**
     * 查询共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result common(@PathVariable Long id){
        return followService.common(id);
    }
}
