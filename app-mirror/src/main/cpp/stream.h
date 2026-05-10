/**
 * @file src/stream.h
 * @brief Declarations for the streaming protocols.
 */
#pragma once

#include <cstdint>

// standard includes
#include <utility>

// lib includes
#include <boost/asio.hpp>

// local includes
#include "audio.h"
#include "crypto.h"
#include "video.h"
#include "rtsp.h"

namespace stream {
  constexpr auto VIDEO_STREAM_PORT = 9;
  constexpr auto CONTROL_PORT = 10;
  constexpr auto AUDIO_STREAM_PORT = 11;

  struct session_t;

  struct config_t {
    audio::config_t audio;
    video::config_t monitor;

    int packetsize;
    int minRequiredFecPackets;
    int mlFeatureFlags;
    int controlProtocolType;
    int audioQosType;
    int videoQosType;

    uint32_t encryptionFlagsEnabled;

    std::optional<int> gcmap;
  };

  struct video_debug_stats_t {
    uint64_t queued_frames;
    uint64_t dropped_frames;
    uint64_t sent_frames;
    uint64_t sent_packets;
    uint64_t sent_bytes;
    uint64_t packetize_us;
    uint64_t fec_us;
    uint64_t send_us;
    uint64_t total_us;
    int64_t last_frame_index;
    int64_t last_queue_delay_ms;
  };

  video_debug_stats_t getVideoDebugStats();
  void resetVideoDebugStats();
  void postFrame(const uint8_t *prefix_data, size_t prefix_size, const uint8_t *frame_data, size_t frame_size, int64_t frame_index, bool idr, void* channel_data);
  void postFrame(std::vector<uint8_t> &&frame_data, int64_t frame_index, bool idr, void* channel_data);

  namespace session {
    enum class state_e : int {
      STOPPED,  ///< The session is stopped
      STOPPING,  ///< The session is stopping
      STARTING,  ///< The session is starting
      RUNNING,  ///< The session is running
    };

    std::shared_ptr<session_t> alloc(config_t &config, rtsp_stream::launch_session_t &launch_session);
    int start(session_t &session, const std::string &addr_string);
    void stop(session_t &session);
    void join(session_t &session);
    state_e state(session_t &session);
      uint getRunningSessions();
  }  // namespace session
}  // namespace stream
