//! JNI exports of `com.wf.wflib.physics.rigid.NarrowNative` (static natives: env, class, args).

const std = @import("std");
const narrow = @import("narrow.zig");

const Env = *const *const [256]?*const anyopaque;
/// JNINativeInterface_ slot (jni.h, reserved0 = 0).
const GET_DIRECT_BUFFER_ADDRESS = 230;

/// Bumped with any layout change of `NarrowNative`.
const VERSION: i32 = 2;

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_version(_: Env, _: ?*anyopaque) callconv(.c) i32 {
    return VERSION;
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_address(env: Env, _: ?*anyopaque, buffer: ?*anyopaque) callconv(.c) i64 {
    const get: *const fn (Env, ?*anyopaque) callconv(.c) ?*anyopaque = @ptrCast(@alignCast(env.*.*[GET_DIRECT_BUFFER_ADDRESS].?));
    return @intCast(@intFromPtr(get(env, buffer)));
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_init(_: Env, _: ?*anyopaque, edge_faces: i64, fused: u8) callconv(.c) void {
    narrow.initTables(@ptrFromInt(@as(usize, @intCast(edge_faces))), fused != 0);
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_createHull(_: Env, _: ?*anyopaque, input: i64) callconv(.c) i64 {
    const h = narrow.createHull(@ptrFromInt(@as(usize, @intCast(input)))) orelse return 0;
    return @intCast(@intFromPtr(h));
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_destroyHull(_: Env, _: ?*anyopaque, handle: i64) callconv(.c) void {
    narrow.destroyHull(@ptrFromInt(@as(usize, @intCast(handle))));
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_create(_: Env, _: ?*anyopaque) callconv(.c) i64 {
    const ctx = std.heap.page_allocator.create(narrow.Context) catch return 0;
    ctx.* = .{};
    return @intCast(@intFromPtr(ctx));
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_destroy(_: Env, _: ?*anyopaque, handle: i64) callconv(.c) void {
    const ctx: *narrow.Context = @ptrFromInt(@as(usize, @intCast(handle)));
    ctx.arena.deinit();
    std.heap.page_allocator.destroy(ctx);
}

export fn Java_com_wf_wflib_physics_rigid_NarrowNative_worldContacts(_: Env, _: ?*anyopaque, handle: i64, input: i64, output: i64, capacity: i32) callconv(.c) i32 {
    const ctx: *narrow.Context = @ptrFromInt(@as(usize, @intCast(handle)));
    return @intCast(narrow.worldContacts(ctx, @ptrFromInt(@as(usize, @intCast(input))), @ptrFromInt(@as(usize, @intCast(output))), @intCast(capacity)));
}
