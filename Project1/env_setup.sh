#!/bin/bash

YELLOW='\033[1;33m'
NC='\033[0m'

set -e
cd ~
sudo apt update -y

# Install Bazel 0.27.0
if [ ! -f ~/bin/bazel ]; then
    echo -e "${YELLOW}[*] Begin to install Bazel...${NC}"
    sudo apt-get install pkg-config zip g++ zlib1g-dev unzip python3 -y
    wget https://github.com/bazelbuild/bazel/releases/download/0.27.0/bazel-0.27.0-installer-linux-x86_64.sh
    chmod +x bazel-0.27.0-installer-linux-x86_64.sh
    ./bazel-0.27.0-installer-linux-x86_64.sh --user
    export PATH="$PATH:$HOME/bin"
    echo 'export PATH="$PATH:$HOME/bin"' >> ~/.bashrc
fi

# Install ONOS 2.2.0
if [ -z "$ONOS_ROOT" ]; then
    echo -e "${YELLOW}[*] Begin to install ONOS...${NC}"
    if [ ! -d ~/onos ]; then
        sudo apt-get install git curl python bzip2 -y
        git clone https://gerrit.onosproject.org/onos
    fi
    cd onos
    git checkout 2.2.0
    grep -lr http://repo1.maven.org/ . | xargs sed -i 's/http:\/\/repo1\.maven\.org/https:\/\/repo1\.maven\.org/g'

    set +e
    bazel build onos
    if [ "$?" -ne 0 ]; then
        set -e
        find ~/.cache -path "*/external/io_grpc_grpc_java/repositories.bzl" | xargs sed -i 's/http:\/\/central\.maven\.org/https:\/\/repo1\.maven\.org/g'
        bazel build onos
    fi
    set -e
    export ONOS_ROOT=~/onos
    echo 'export ONOS_ROOT=~/onos' >> ~/.bashrc
    echo 'source $ONOS_ROOT/tools/dev/bash_profile' >> ~/.bashrc
fi

# Install mininet
if [ -z "$(which mn)" ]; then
    echo -e "${YELLOW}[*] Begin to install mininet...${NC}"
    git clone git://github.com/mininet/mininet
    mininet/util/install.sh -n
fi

# Install OvS 2.11.0
if [ -z "$(which ovs-vsctl)" ]; then
    echo -e "${YELLOW}[*] Begin to install OvS...${NC}"
    wget https://www.openvswitch.org/releases/openvswitch-2.11.0.tar.gz
    tar zxf openvswitch-2.11.0.tar.gz
    cd openvswitch-2.11.0
    ./configure
    make
    sudo make install
    sudo mkdir -p /usr/local/etc/openvswitch
    sudo ovsdb-tool create /usr/local/etc/openvswitch/conf.db vswitchd/vswitch.ovsschema
    sudo /usr/local/share/openvswitch/scripts/ovs-ctl start
fi

# Finish
sudo sed -i "/exit 0$/i ovsdb-server --remote=punix:/usr/local/var/run/openvswitch/db.sock \
                     --remote=db:Open_vSwitch,Open_vSwitch,manager_options \
                     --private-key=db:Open_vSwitch,SSL,private_key \
                     --certificate=db:Open_vSwitch,SSL,certificate \
                     --bootstrap-ca-cert=db:Open_vSwitch,SSL,ca_cert \
                     --pidfile --detach \novs-vsctl --no-wait init \novs-vswitchd --pidfile --detach" /etc/rc.local
echo -e "${YELLOW}*** Installation Finished! ***${NC}"
