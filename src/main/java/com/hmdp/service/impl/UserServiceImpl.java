package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.ErrorMessage;
import com.hmdp.constant.LoginConstant;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @return
     */
    @Override
    public Result sentCode(String phone) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //不符合，返回错误信息
            return Result.fail(ErrorMessage.PHONE_NUMBER_FORMAT_ERROR);
        }

        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LoginConstant.KEY_OF_CODE_PREFIX+phone,code,LoginConstant.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码  TODO 此处为假的实现
        log.debug("发送短信验证码："+code);

        return Result.ok();
    }

    /**
     * 登录
     * @param loginForm
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {

        String phone = loginForm.getPhone();

        //检验验证码
        String code = stringRedisTemplate.opsForValue().get(LoginConstant.KEY_OF_CODE_PREFIX + phone);
        if(code==null||!code.equals(loginForm.getCode())){
            return Result.fail(ErrorMessage.CODE_ERROR);
        }

        //根据手机号查询用户
        User user = query().eq(LoginConstant.PHONE, phone).one();

        //检验用户是否存在
        if(user==null){
            //不存在，创建新用户
            user=createUser(phone);
        }

        //将用户保存到Redis中
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);

        //将User对象转为Hash存储
        UserDTO userDTO= BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        String userKey=LoginConstant.KEY_OF_USER_PREFIX+token;
        stringRedisTemplate.opsForHash().putAll(userKey,userMap);

        //设置token有效期
        stringRedisTemplate.expire(userKey,LoginConstant.LOGIN_USER_TTL,TimeUnit.MINUTES);

        //返回token
        return Result.ok(token);
    }

    //创建用户 TODO 创建时间没设置
    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(LoginConstant.USER_PREFIX+RandomUtil.randomString(5));
        save(user);
        return user;
    }
}
