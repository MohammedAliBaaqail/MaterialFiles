cmake_minimum_required(VERSION 3.4.1)

add_definitions(-DANDROID)
include_directories(crypto)

find_library(log-lib log)

# AES
add_library(edsaes SHARED
            aes/edsaes.c 
            aes/aescrypt.c 
            aes/aeskey.c 
            aes/aestab.c)
target_link_libraries(edsaes ${log-lib})

# XTS Mode
add_library(edsxts SHARED
            xts/edsxts.c)
target_link_libraries(edsxts ${log-lib})

# Serpent
add_library(edsserpent SHARED
            serpent/edsserpent.c
            serpent/serpent.c)
target_link_libraries(edsserpent ${log-lib})

# Twofish
add_library(edstwofish SHARED
            twofish/edstwofish.c
            twofish/twofish.c)
target_link_libraries(edstwofish ${log-lib})

# RIPEMD160
add_library(edsripemd160 SHARED
            ripemd160/edsripemd160.c)
target_link_libraries(edsripemd160 ${log-lib})

# Whirlpool
add_library(edswhirlpool SHARED
            whirlpool/edswhirlpool.c
            whirlpool/whirlpool.c)
target_link_libraries(edswhirlpool ${log-lib})

# Direct IO / AIO (if needed for performance)
add_library(fdraio SHARED
            fdraio/fdraio.c)
target_compile_definitions(fdraio PRIVATE _FILE_OFFSET_BITS=64)
target_link_libraries(fdraio ${log-lib})

# Local XTS
add_library(localxts SHARED
            localxts/localxts.c)
target_compile_definitions(localxts PRIVATE _FILE_OFFSET_BITS=64)
target_link_libraries(localxts edsxts ${log-lib})

# CTR
add_library(edsctr SHARED
            ctr/edsctr.c)
target_link_libraries(edsctr ${log-lib})

# CBC
add_library(edscbc SHARED
            cbc/edscbc.c)
target_link_libraries(edscbc ${log-lib})

# CFB
add_library(edscfb SHARED
            cfb/edscfb.c)
target_link_libraries(edscfb ${log-lib})

# GOST
add_library(edsgost SHARED
            gost/edsgost.c
            gost/gost89.c)
target_link_libraries(edsgost ${log-lib})
