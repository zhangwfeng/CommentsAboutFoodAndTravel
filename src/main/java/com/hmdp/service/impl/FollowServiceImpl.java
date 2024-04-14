package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.constant.CacheConstant;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long id, Boolean isFollow) {
        //查询用户
        UserDTO user = UserHolder.getUser();
        if(user==null){
            //用户未登录，不能关注
            return Result.fail(ErrorMessage.NOT_LOGIN);
        }

        //用户进行关注
        Long userId = user.getId();
        String key= CacheConstant.FOLLOW_PREFIX+userId;
        if(isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,id.toString());
            }
        }

        //用户进行取关
        else {
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,id.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        //查询用户
        UserDTO user = UserHolder.getUser();
        if(user==null){
            //用户未登录，不能关注
            return Result.ok(false);
        }

        Long userId = user.getId();
        String key = CacheConstant.FOLLOW_PREFIX + userId;
        Boolean follow = stringRedisTemplate.opsForSet().isMember(key, id.toString());
        //Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(BooleanUtil.isTrue(follow));
    }

    @Override
    public Result common(Long id) {
        //查询用户
        UserDTO user = UserHolder.getUser();
        if(user==null){
            //用户未登录，不能关注
            return Result.ok(false);
        }

        Long userId = user.getId();
        String key1 = CacheConstant.FOLLOW_PREFIX + userId;
        String key2 = CacheConstant.FOLLOW_PREFIX + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect==null||intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
