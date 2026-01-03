package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.BeanUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendcode(String phone, HttpSession session) {

        // 1.判断提交的手机号是否合规
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1.1 不符合则返回错误信息
            return Result.fail("手机号不符合规范");

        }

        // 1.2 符合生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 2 保存验证码和手机号到Redis（绑定验证码与手机号）
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 3 发送验证码
        log.debug("发送验证码成功，验证码为 {}", code);

        return Result.ok();

    }


    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 实现登录功能
        //验证发送的验证码和手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        
        // 验证手机号是否与发送验证码时的手机号一致
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
//        Object sessionPhone = session.getAttribute("phone");
//        if (sessionPhone == null || !sessionPhone.equals(phone)) {
//            return Result.fail("手机号与发送验证码的手机号不一致");
//        }
        
        // 验证验证码是否正确
        if (code.isEmpty()) {
            return Result.fail("验证码错误");
        }
        //如果一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //查询用户是否存在

        if (user == null) {
            //如果不存在，添加到数据库
            user = saveWithPhone(phone);
        }

        //如果存在，保存用户到Redis

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> useMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieValue) -> fieValue.toString()));
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, useMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User saveWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
