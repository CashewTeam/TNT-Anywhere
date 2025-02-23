#include "common.h"

namespace fs = std::filesystem;

namespace platf {
    void adjust_thread_priority(thread_priority_e priority) {
        // TODO
    }
    fs::path appdata() {
        // TODO
        return {};
    }
    std::string get_host_name() {
        // TODO
        return "屏易连";
    }
    std::vector<supported_gamepad_t> gamepads_list {
    };
    std::vector<supported_gamepad_t> &supported_gamepads(input_t *input) {
        // TODO
        return gamepads_list;
    }
    bool send_batch(batched_send_info_t &send_info) {
        // TODO
        return false;
    }
    std::string get_mac_address(const std::string_view &address) {
        // TODO
        return {};
    }
    
  platform_caps::caps_t get_capabilities() {
    platform_caps::caps_t caps = 0;
    // TODO: if has_uinput
    caps |= platform_caps::pen_touch;

    // We support controller touchpad input only when emulating the PS5 controller
    if (config::input.gamepad == "ds5"sv || config::input.gamepad == "auto"sv) {
      caps |= platform_caps::controller_touch;
    }

    return caps;
  }
    std::string from_sockaddr(const sockaddr *const ip_addr) {
        char data[INET6_ADDRSTRLEN] = {};

        auto family = ip_addr->sa_family;
        if (family == AF_INET6) {
            inet_ntop(AF_INET6, &((sockaddr_in6 *) ip_addr)->sin6_addr, data, INET6_ADDRSTRLEN);
        } else if (family == AF_INET) {
            inet_ntop(AF_INET, &((sockaddr_in *) ip_addr)->sin_addr, data, INET_ADDRSTRLEN);
        }

        return std::string {data};
    }
    std::pair<std::uint16_t, std::string> from_sockaddr_ex(const sockaddr *const ip_addr) {
        char data[INET6_ADDRSTRLEN] = {};

        auto family = ip_addr->sa_family;
        std::uint16_t port = 0;
        if (family == AF_INET6) {
            inet_ntop(AF_INET6, &((sockaddr_in6 *) ip_addr)->sin6_addr, data, INET6_ADDRSTRLEN);
            port = ((sockaddr_in6 *) ip_addr)->sin6_port;
        } else if (family == AF_INET) {
            inet_ntop(AF_INET, &((sockaddr_in *) ip_addr)->sin_addr, data, INET_ADDRSTRLEN);
            port = ((sockaddr_in *) ip_addr)->sin_port;
        }

        return {port, std::string{data}};
    }
    class linux_high_precision_timer: public high_precision_timer {
    public:
        void sleep_for(const std::chrono::nanoseconds &duration) override {
            std::this_thread::sleep_for(duration);
        }

        operator bool() override {
            return true;
        }
    };

    std::unique_ptr<high_precision_timer> create_high_precision_timer() {
        return std::make_unique<linux_high_precision_timer>();
    }

    struct sockaddr_in to_sockaddr(boost::asio::ip::address_v4 address, uint16_t port) {
        struct sockaddr_in saddr_v4 = {};

        saddr_v4.sin_family = AF_INET;
        saddr_v4.sin_port = htons(port);

        auto addr_bytes = address.to_bytes();
        memcpy(&saddr_v4.sin_addr, addr_bytes.data(), sizeof(saddr_v4.sin_addr));

        return saddr_v4;
    }

    struct sockaddr_in6 to_sockaddr(boost::asio::ip::address_v6 address, uint16_t port) {
        struct sockaddr_in6 saddr_v6 = {};

        saddr_v6.sin6_family = AF_INET6;
        saddr_v6.sin6_port = htons(port);
        saddr_v6.sin6_scope_id = address.scope_id();

        auto addr_bytes = address.to_bytes();
        memcpy(&saddr_v6.sin6_addr, addr_bytes.data(), sizeof(saddr_v6.sin6_addr));

        return saddr_v6;
    }

    // We can't track QoS state separately for each destination on this OS,
    // so we keep a ref count to only disable QoS options when all clients
    // are disconnected.
    static std::atomic<int> qos_ref_count = 0;

    class qos_t: public deinit_t {
    public:
        qos_t(int sockfd, std::vector<std::tuple<int, int, int>> options):
                sockfd(sockfd),
                options(options) {
            qos_ref_count++;
        }

        virtual ~qos_t() {
            if (--qos_ref_count == 0) {
                for (const auto &tuple : options) {
                    auto reset_val = std::get<2>(tuple);
                    if (setsockopt(sockfd, std::get<0>(tuple), std::get<1>(tuple), &reset_val, sizeof(reset_val)) < 0) {
                        BOOST_LOG(warning) << "Failed to reset option: "sv << errno;
                    }
                }
            }
        }

    private:
        int sockfd;
        std::vector<std::tuple<int, int, int>> options;
    };

    /**
     * @brief Enables QoS on the given socket for traffic to the specified destination.
     * @param native_socket The native socket handle.
     * @param address The destination address for traffic sent on this socket.
     * @param port The destination port for traffic sent on this socket.
     * @param data_type The type of traffic sent on this socket.
     * @param dscp_tagging Specifies whether to enable DSCP tagging on outgoing traffic.
     */
    std::unique_ptr<deinit_t> enable_socket_qos(uintptr_t native_socket, boost::asio::ip::address &address, uint16_t port, qos_data_type_e data_type, bool dscp_tagging) {
        int sockfd = (int) native_socket;
        std::vector<std::tuple<int, int, int>> reset_options;

        if (dscp_tagging) {
            int level;
            int option;

            // With dual-stack sockets, Linux uses IPV6_TCLASS for IPv6 traffic
            // and IP_TOS for IPv4 traffic.
            if (address.is_v6() && !address.to_v6().is_v4_mapped()) {
                level = SOL_IPV6;
                option = IPV6_TCLASS;
            } else {
                level = SOL_IP;
                option = IP_TOS;
            }

            // The specific DSCP values here are chosen to be consistent with Windows,
            // except that we use CS6 instead of CS7 for audio traffic.
            int dscp = 0;
            switch (data_type) {
                case qos_data_type_e::video:
                    dscp = 40;
                    break;
                case qos_data_type_e::audio:
                    dscp = 48;
                    break;
                default:
                    BOOST_LOG(error) << "Unknown traffic type: "sv << (int) data_type;
                    break;
            }

            if (dscp) {
                // Shift to put the DSCP value in the correct position in the TOS field
                dscp <<= 2;

                if (setsockopt(sockfd, level, option, &dscp, sizeof(dscp)) == 0) {
                    // Reset TOS to -1 when QoS is disabled
                    reset_options.emplace_back(std::make_tuple(level, option, -1));
                } else {
                    BOOST_LOG(error) << "Failed to set TOS/TCLASS: "sv << errno;
                }
            }
        }

        // We can use SO_PRIORITY to set outgoing traffic priority without DSCP tagging.
        //
        // NB: We set this after IP_TOS/IPV6_TCLASS since setting TOS value seems to
        // reset SO_PRIORITY back to 0.
        //
        // 6 is the highest priority that can be used without SYS_CAP_ADMIN.
        int priority = data_type == qos_data_type_e::audio ? 6 : 5;
        if (setsockopt(sockfd, SOL_SOCKET, SO_PRIORITY, &priority, sizeof(priority)) == 0) {
            // Reset SO_PRIORITY to 0 when QoS is disabled
            reset_options.emplace_back(std::make_tuple(SOL_SOCKET, SO_PRIORITY, 0));
        } else {
            BOOST_LOG(error) << "Failed to set SO_PRIORITY: "sv << errno;
        }

        return std::make_unique<qos_t>(sockfd, reset_options);
    }


    bool send(send_info_t &send_info) {
        auto sockfd = (int) send_info.native_socket;
        struct msghdr msg = {};

        // Convert the target address into a sockaddr
        struct sockaddr_in taddr_v4 = {};
        struct sockaddr_in6 taddr_v6 = {};
        if (send_info.target_address.is_v6()) {
            taddr_v6 = to_sockaddr(send_info.target_address.to_v6(), send_info.target_port);

            msg.msg_name = (struct sockaddr *) &taddr_v6;
            msg.msg_namelen = sizeof(taddr_v6);
        } else {
            taddr_v4 = to_sockaddr(send_info.target_address.to_v4(), send_info.target_port);

            msg.msg_name = (struct sockaddr *) &taddr_v4;
            msg.msg_namelen = sizeof(taddr_v4);
        }

        union {
            char buf[std::max(CMSG_SPACE(sizeof(struct in_pktinfo)), CMSG_SPACE(sizeof(struct in6_pktinfo)))];
            struct cmsghdr alignment;
        } cmbuf;

        socklen_t cmbuflen = 0;

        msg.msg_control = cmbuf.buf;
        msg.msg_controllen = sizeof(cmbuf.buf);

        auto pktinfo_cm = CMSG_FIRSTHDR(&msg);
        if (send_info.source_address.is_v6()) {
            struct in6_pktinfo pktInfo;

            struct sockaddr_in6 saddr_v6 = to_sockaddr(send_info.source_address.to_v6(), 0);
            pktInfo.ipi6_addr = saddr_v6.sin6_addr;
            pktInfo.ipi6_ifindex = 0;

            cmbuflen += CMSG_SPACE(sizeof(pktInfo));

            pktinfo_cm->cmsg_level = IPPROTO_IPV6;
            pktinfo_cm->cmsg_type = IPV6_PKTINFO;
            pktinfo_cm->cmsg_len = CMSG_LEN(sizeof(pktInfo));
            memcpy(CMSG_DATA(pktinfo_cm), &pktInfo, sizeof(pktInfo));
        } else {
            struct in_pktinfo pktInfo;

            struct sockaddr_in saddr_v4 = to_sockaddr(send_info.source_address.to_v4(), 0);
            pktInfo.ipi_spec_dst = saddr_v4.sin_addr;
            pktInfo.ipi_ifindex = 0;

            cmbuflen += CMSG_SPACE(sizeof(pktInfo));

            pktinfo_cm->cmsg_level = IPPROTO_IP;
            pktinfo_cm->cmsg_type = IP_PKTINFO;
            pktinfo_cm->cmsg_len = CMSG_LEN(sizeof(pktInfo));
            memcpy(CMSG_DATA(pktinfo_cm), &pktInfo, sizeof(pktInfo));
        }

        struct iovec iovs[2] = {};
        int iovlen = 0;
        if (send_info.header) {
            iovs[iovlen].iov_base = (void *) send_info.header;
            iovs[iovlen].iov_len = send_info.header_size;
            iovlen++;
        }
        iovs[iovlen].iov_base = (void *) send_info.payload;
        iovs[iovlen].iov_len = send_info.payload_size;
        iovlen++;

        msg.msg_iov = iovs;
        msg.msg_iovlen = iovlen;

        msg.msg_controllen = cmbuflen;

        auto bytes_sent = sendmsg(sockfd, &msg, 0);

        // If there's no send buffer space, wait for some to be available
        while (bytes_sent < 0 && errno == EAGAIN) {
            struct pollfd pfd;

            pfd.fd = sockfd;
            pfd.events = POLLOUT;

            if (poll(&pfd, 1, -1) != 1) {
                BOOST_LOG(warning) << "poll() failed: "sv << errno;
                break;
            }

            // Try to send again
            bytes_sent = sendmsg(sockfd, &msg, 0);
        }

        if (bytes_sent < 0) {
            BOOST_LOG(warning) << "sendmsg() failed: "sv << errno;
            return false;
        }

        return true;
    }
}