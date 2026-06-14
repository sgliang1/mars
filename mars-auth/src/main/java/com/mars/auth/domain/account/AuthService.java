package com.mars.auth.domain.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mars.auth.domain.account.LoginRequest;
import com.mars.auth.domain.account.LoginResponse; // �?必须导入这个
import com.mars.auth.domain.account.User;
import com.mars.auth.domain.account.UserMapper;
import com.mars.auth.domain.account.UserProfile;
import com.mars.auth.domain.account.UserProfileMapper;
import com.mars.auth.domain.account.ProfileDashboardDTO;
import com.mars.common.Result;
import com.mars.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    // 密码加密�?
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // �?修改返回值泛型为 LoginResponse
    public Result<LoginResponse> login(LoginRequest request) {
        // 1. 查用�?
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            return Result.fail("用户不存在");
        }

        // 2. 校验密码 (兼容明文和密�?
        boolean isMatch = false;
        // 如果数据库里存的�?"123456" 这种短明�?
        if (user.getPassword().length() < 50 && user.getPassword().equals(request.getPassword())) {
            isMatch = true;
        }
        // 如果数据库里是加密后的长字符�?
        else if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            isMatch = true;
        }

        if (!isMatch) {
            return Result.fail("密码错误");
        }

        // 3. 生成 Token
        String token = JwtUtil.generateToken(user.getId(), user.getUsername());

        // ✅✅�?关键修复�?
        // 之前 Result.success(token) 会被识别�?success(String msg)，导�?Token 变成�?msg 字段�?
        // 现在改为返回 LoginResponse 对象，确�?Token 进入 data 字段�?
        return Result.success(new LoginResponse(token, user.getId(), user.getUsername()));
    }

    // 注册逻辑
    public Result register(User user) {
        // 简单校�?
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername()));
        if (exist != null) return Result.fail("用户名已存在");

        // 注册时加密密�?
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return Result.success("注册成功");
    }

    // �?AuthService 类中添加
    public Result update(User user) {
        if (user.getId() == null) {
            return Result.fail("用户ID不能为空");
        }

        // 如果修改了用户名，需要检查是否与其他用户重复
        if (user.getUsername() != null) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, user.getUsername())
                    .ne(User::getId, user.getId())); // 排除自己
            if (exist != null) {
                return Result.fail("用户�?昵h称已存在");
            }
        }

        // 仅更新非空字�?
        userMapper.updateById(user);

        // 如果修改了用户名，最好返回新�?User 对象�?token，这里简单返回成�?
        return Result.success("更新成功");
    }

    // ======================================
    // 个人中心仪表盘数据组装
    // ======================================
    public Result<ProfileDashboardDTO> getDashboard(Long userId, HttpServletRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return Result.fail("用户资料尚未初始化");
        }

        ProfileDashboardDTO dto = new ProfileDashboardDTO();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatarUrl(profile.getAvatarUrl());
        dto.setBio(profile.getBio());
        dto.setGender(profile.getGender());
        dto.setBirthday(profile.getBirthday());
        dto.setIpLocation(resolveIpLocation(request));
        
        // 关键性能点：直接读取冗余字段，时间复杂度 O(1)
        dto.setFollowingCount(profile.getFollowingCount() == null ? 0 : profile.getFollowingCount());
        dto.setFollowerCount(profile.getFollowerCount() == null ? 0 : profile.getFollowerCount());
        dto.setTotalLikedCount(profile.getTotalLikedCount() == null ? 0 : profile.getTotalLikedCount());
        dto.setPostCount(0); // 暂定为0，后续联调 mars-post 补充
        
        return Result.success(dto);
    }

    private String resolveIpLocation(HttpServletRequest request) {
        if (request == null) return "未知";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        // TODO: 接入 IP 归属地 SDK（如 ip2region）解析真实地理位置
        return ip != null && !ip.isBlank() ? ip : "未知";
    }

    public Result updateProfile(UpdateProfileRequest request) {
        if (request.getUserId() == null) {
            return Result.fail("用户ID不能为空");
        }

        // 1. 更新 user 表（用户名）
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername())
                    .ne(User::getId, request.getUserId()));
            if (exist != null) {
                return Result.fail("昵称已被占用");
            }
            User user = new User();
            user.setId(request.getUserId());
            user.setUsername(request.getUsername());
            userMapper.updateById(user);
        }

        // 2. 查询或初始化 user_profile
        UserProfile profile = userProfileMapper.selectById(request.getUserId());
        final boolean isNewProfile = (profile == null);
        if (isNewProfile) {
            profile = new UserProfile();
            profile.setUserId(request.getUserId());
        }

        boolean needUpdateProfile = false;
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
            needUpdateProfile = true;
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
            needUpdateProfile = true;
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender());
            needUpdateProfile = true;
        }
        if (request.getBirthday() != null) {
            profile.setBirthday(request.getBirthday());
            needUpdateProfile = true;
        }

        if (needUpdateProfile) {
            if (isNewProfile) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }
        }

        return Result.success("资料更新成功");
    }
}
