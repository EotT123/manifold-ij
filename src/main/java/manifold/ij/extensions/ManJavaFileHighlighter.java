/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lexer.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import manifold.ext.rt.api.Jailbreak;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

//!!
//!! so we can handle '$' as a legal escape char (for string literal templates)
//!!
public class ManJavaFileHighlighter extends JavaFileHighlighter
{
  public ManJavaFileHighlighter( LanguageLevel languageLevel )
  {
    super( languageLevel );
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer()
  {
    @Jailbreak LayeredLexer highlightingLexer = (LayeredLexer)super.getHighlightingLexer();

    ReflectUtil.ConstructorRef lexerCtor = ReflectUtil.constructor( "com.intellij.lexer.JavaStringLiteralLexer", char.class, IElementType.class, boolean.class, String.class );

    highlightingLexer.myStartTokenToLayerLexer.remove( JavaTokenType.STRING_LITERAL ); // remove existing additional esc chars, so we can re-add them plus the $
    highlightingLexer.registerSelfStoppingLayer( (Lexer)lexerCtor.newInstance( '\"', JavaTokenType.STRING_LITERAL, false, "s{$"),
                                                 new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);

    highlightingLexer.myStartTokenToLayerLexer.remove( JavaTokenType.TEXT_BLOCK_LITERAL ); // remove existing additional esc chars, so we can re-add them plus the $
    highlightingLexer.registerSelfStoppingLayer( (Lexer)lexerCtor.newInstance( StringLiteralLexer.NO_QUOTE_CHAR, JavaTokenType.TEXT_BLOCK_LITERAL, true, "s{$"),
                                                 new IElementType[]{JavaTokenType.TEXT_BLOCK_LITERAL}, IElementType.EMPTY_ARRAY);
    return highlightingLexer;
  }
}
