/*
 * Copyright 2023 Santos Zatarain Vera
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mx.com.inftel.codegen.plugin

internal fun capitalized(str: String): String = str.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

internal fun decapitalized(str: String): String = str.replaceFirstChar { it.lowercase() }
