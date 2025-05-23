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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.preprocessor.PreprocessorParser;
import manifold.preprocessor.TokenType;
import manifold.preprocessor.Tokenizer;
import manifold.rt.api.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For the Preprocessor.  Highlights preprocessor directives and shades masked out code (which are PsiComments).
 */
public class ManPreprocessorAnnotator extends ExternalAnnotator<PsiFile, ManPreprocessorAnnotator.Info>
{
  @Nullable
  @Override
  public PsiFile collectInformation( @NotNull PsiFile file )
  {
    return file;
  }

  @Nullable
  @Override
  public PsiFile collectInformation( @NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors )
  {
    return file;
  }

  @Nullable
  @Override
  public Info doAnnotate( PsiFile file )
  {
    if( !ManProject.isManifoldInUse( file ) )
    {
      // Manifold jars are not used in the project
      return new Info();
    }

    return ApplicationManager.getApplication().runReadAction( (Computable<Info>) () -> {
      ManModule module = ManProject.getModule( file );
      if( module == null || !module.isPreprocessorEnabled() )
      {
        return new Info();
      }

      Info info = new Info();
      String source = file.getText();
      PreprocessorParser parser = new PreprocessorParser( source, t -> addDirective( t, info ) );
      parser.parseFile( ( message, pos ) -> {
        int errorPos = ensureErrorPosNotOnNewLine( source, pos );
        info.addToIssues( message, errorPos );
      } );
      return info;
    } );
  }

  private int ensureErrorPosNotOnNewLine( String source, int pos )
  {
    char c = pos < source.length() ? source.charAt( pos ) : '\0';
    if( pos > 0 && c == '\0' || c == '\r' || c == '\n' )
    {
      pos--;
    }
    return pos;
  }


  @Override
  public void apply( @NotNull PsiFile file, Info info, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( file ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    ManModule module = ManProject.getModule( file );
    if( module == null || !module.isPreprocessorEnabled() )
    {
      // preprocessor not in use in module
      return;
    }

    info.getDirectives().forEach( d -> {
      holder.newAnnotation( HighlightSeverity.INFORMATION, "" )
        .range( TextRange.create( d[0], d[1] ) )
        .enforcedTextAttributes( directiveAttr() )
        .create();
      PsiElement comment = file.findElementAt( d[0] + 1 );
      if( comment instanceof PsiComment )
      {
        if( comment.getText().startsWith( "#" + TokenType.Error.getDirective() ) )
        {
          holder.newAnnotation( HighlightSeverity.ERROR, "" ).range( TextRange.create( d[0], d[1] ) ).create();
        }
        else if( comment.getText().startsWith( "#" + TokenType.Warning.getDirective() ) )
        {
          holder.newAnnotation( HighlightSeverity.WARNING, "" ).range( TextRange.create( d[0], d[1] ) ).create();
        }
        holder.newAnnotation( HighlightSeverity.INFORMATION, "" )
          .range( comment )
          .enforcedTextAttributes( maskedCodeAttr() )
          .create();
      }
    } );
    info.getIssues().forEach(
      issue -> holder.newAnnotation( HighlightSeverity.ERROR, issue.getFirst() )
        .range( TextRange.create( issue.getSecond(), issue.getSecond() + 1 ) )
        .create() );
  }

  private @NotNull TextAttributes directiveAttr() {
    Color color = EditorColorsUtil.getGlobalOrDefaultColor( ManColorSettingsPage.KEY_PREPROCESSOR_DIRECTIVE );
    return new TextAttributes( color, null, null, null, Font.BOLD );
  }

  private @NotNull TextAttributes maskedCodeAttr() {
    TextAttributes attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes( ManColorSettingsPage.PREPROCESSOR_MASKED_CODE );
    Color foregroundColor = attrs.getForegroundColor();
    Color backgroundColor = attrs.getBackgroundColor();
    return new TextAttributes( foregroundColor, backgroundColor, attrs.getEffectColor(), attrs.getEffectType(), attrs.getFontType() );
  }

  private void addDirective( Tokenizer tokenizer, Info info )
  {
    if( tokenizer.getTokenType() == null )
    {
      return;
    }

    switch( tokenizer.getTokenType() )
    {
      case If:
      case Elif:
      case Else:
      case Endif:
      case Define:
      case Undef:
      case Error:
      case Warning:
        info.addToDirectives( tokenizer.getTokenStart(),
          tokenizer.getTokenStart()+1 + tokenizer.getTokenType().getDirective().length() );
        break;
    }
  }

  static class Info
  {
    List<int[]> _directives;
    List<Pair<String, Integer>> _issues;

    Info()
    {
      _directives = new ArrayList<>();
      _issues = new ArrayList<>();
    }

    void addToDirectives( int start, int end )
    {
      _directives.add( new int[] {start, end} );
    }

    void addToIssues( String message, int pos )
    {
      _issues.add( new Pair<>( message, pos ) );
    }

    List<int[]> getDirectives()
    {
      return _directives;
    }

    List<Pair<String, Integer>> getIssues()
    {
      return _issues;
    }
  }
}
