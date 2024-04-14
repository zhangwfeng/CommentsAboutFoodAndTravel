package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.CacheConstant;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
                isBlogLike(blog);
        });
        return Result.ok(records);
    }



    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail(ErrorMessage.BLOG_INEXIST);
        }
        queryBlogUser(blog);
        //查询并设置blog是否被点赞
        isBlogLike(blog);
        return Result.ok(blog);
    }

    private void isBlogLike(Blog blog) {
        //1、获取登录用户
        UserDTO user = UserHolder.getUser();
        if(user==null){
            //用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();

        //2、判断当前登录用户是否已经点赞
        String key = CacheConstant.BLOG_LIKE_PREFIX + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        blog.setIsLike(score!=null);
    }

    @Override
    public Result likeId(Long id) {
        //1、获取登录用户
        Long userId = UserHolder.getUser().getId();

        //2、判断当前登录用户是否已经点赞
        String key = CacheConstant.BLOG_LIKE_PREFIX + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        //3、如果未点赞，可以点赞
        if(score==null){
            //3.1、数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //3.2、保存用户到redis的set集合
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }
        //4、如果已点赞，取消点赞
        else {
            //3.1、数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            //3.2、删除用户在redis的set集合中的数据
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }


        return Result.ok();
    }


    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    //查询top5的点赞用户
    @Override
    public Result queryBlogLieks(Long id) {
        String key = CacheConstant.BLOG_LIKE_PREFIX + id;
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5Id==null || top5Id.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr= StrUtil.join(",",ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids)
                .last("order by field(id,"+idStr+")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
