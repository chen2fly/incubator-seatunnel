/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import axios, { AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios'
import utils from '@/utils'

const handleError = (res: AxiosResponse<any, any>) => {
  if (import.meta.env.MODE === 'development') {
    utils.log.capsule('SeaTunnel', 'UI')
    utils.log.error(res)
  }
  window.$message.error(res.data.msg)
}

const baseRequestConfig: AxiosRequestConfig = {
  timeout: 6000
}

const service = axios.create(baseRequestConfig)

const err = (err: AxiosError): Promise<AxiosError> => {
  return Promise.reject(err)
}

service.interceptors.request.use((config: AxiosRequestConfig<any>) => {
  return config
}, err)

service.interceptors.response.use((res: AxiosResponse) => {
  return res.data
}, err)

export { service as axios }
