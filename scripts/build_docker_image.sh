#/!bin/sh
#
# Copyright (C) 2020 Bosch.IO GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

pushd $(git rev-parse --show-cdup)

# Use an existing Gradle installation cache as the read-only cache even if not recommended "because this directory may
# contain locks and may be modified by the seeding build", see [1]. This is to speed up local Docker builds when we can
# be sure that no concurrent Gradle build in running.
#
# [1] https://docs.gradle.org/6.2/userguide/dependency_resolution.html#sub:shared-readonly-cache

${GRADLE_USER_HOME:=$HOME/.gradle}
DOCKER_BUILDKIT=1 docker build --build-arg GRADLE_RO_DEP_CACHE= -t ort .

popd
