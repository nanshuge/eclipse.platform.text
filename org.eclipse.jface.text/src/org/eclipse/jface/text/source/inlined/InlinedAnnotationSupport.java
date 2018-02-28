/**
 *  Copyright (c) 2017, 2018 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - [CodeMining] Provide inline annotations support - Bug 527675
 */
package org.eclipse.jface.text.source.inlined;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextLineSpacingProvider;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.ITextViewerExtension4;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.IViewportListener;
import org.eclipse.jface.text.JFaceTextUtil;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationPainter;
import org.eclipse.jface.text.source.AnnotationPainter.IDrawingStrategy;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.IAnnotationModelExtension2;
import org.eclipse.jface.text.source.ISourceViewer;

/**
 * Support to draw inlined annotations:
 *
 * <ul>
 * <li>line header annotation with {@link LineHeaderAnnotation}.</li>
 * <li>line content annotation with {@link LineContentAnnotation}.</li>
 * </ul>
 *
 * @since 3.13
 */
public class InlinedAnnotationSupport implements StyledTextLineSpacingProvider {

	/**
	 * The annotation inlined strategy singleton.
	 */
	private static final IDrawingStrategy INLINED_STRATEGY= new InlinedAnnotationDrawingStrategy();

	/**
	 * The annotation inlined strategy ID.
	 */
	private static final String INLINED_STRATEGY_ID= "inlined"; //$NON-NLS-1$

	/**
	 * Listener used to update {@link GlyphMetrics} width style for {@link LineContentAnnotation}.
	 */
	private ITextPresentationListener updateStylesWidth;

	/**
	 * Class to update {@link GlyphMetrics} width style for {@link LineContentAnnotation}.
	 *
	 */
	private class UpdateStylesWidth implements ITextPresentationListener {

		@Override
		public void applyTextPresentation(TextPresentation textPresentation) {
			IAnnotationModel annotationModel= fViewer.getAnnotationModel();
			IRegion region= textPresentation.getExtent();
			((IAnnotationModelExtension2) annotationModel)
					.getAnnotationIterator(region.getOffset(), region.getLength(), true, true)
					.forEachRemaining(annotation -> {
						if (annotation instanceof LineContentAnnotation) {
							LineContentAnnotation ann= (LineContentAnnotation) annotation;
							Position position= ann.getPosition();
							if (position != null) {
								StyleRange s= new StyleRange();
								s.start= position.getOffset();
								s.length= 1;
								s.metrics= ann.isMarkedDeleted()
										? null
										: new GlyphMetrics(0, 0, ann.getWidth());
								textPresentation.mergeStyleRange(s);
							}
						}
					});
		}
	}

	/**
	 * Tracker of start/end offset of visible lines.
	 */
	private VisibleLines visibleLines;

	/**
	 * Class to track start/end offset of visible lines.
	 *
	 */
	private class VisibleLines implements IViewportListener {

		private int startOffset;

		private int endOffset;

		public VisibleLines() {
			fViewer.getTextWidget().getDisplay().asyncExec(() -> {
				startOffset= getInclusiveTopIndexStartOffset();
				endOffset= getExclusiveBottomIndexEndOffset();
			});
		}

		@Override
		public void viewportChanged(int verticalOffset) {
			compute();
		}

		private void compute() {
			startOffset= getInclusiveTopIndexStartOffset();
			endOffset= getExclusiveBottomIndexEndOffset();
		}

		/**
		 * Returns the document offset of the upper left corner of the source viewer's view port,
		 * possibly including partially visible lines.
		 *
		 * @return the document offset if the upper left corner of the view port
		 */
		private int getInclusiveTopIndexStartOffset() {
			if (fViewer != null && fViewer.getTextWidget() != null && !fViewer.getTextWidget().isDisposed()) {
				int top= JFaceTextUtil.getPartialTopIndex(fViewer);
				try {
					IDocument document= fViewer.getDocument();
					return document.getLineOffset(top);
				} catch (BadLocationException x) {
					// Do nothing
				}
			}
			return -1;
		}

		/**
		 * Returns the first invisible document offset of the lower right corner of the source
		 * viewer's view port, possibly including partially visible lines.
		 *
		 * @return the first invisible document offset of the lower right corner of the view port
		 */
		private int getExclusiveBottomIndexEndOffset() {
			if (fViewer != null && fViewer.getTextWidget() != null && !fViewer.getTextWidget().isDisposed()) {
				int bottom= JFaceTextUtil.getPartialBottomIndex(fViewer);
				try {
					IDocument document= fViewer.getDocument();
					if (bottom >= document.getNumberOfLines()) {
						bottom= document.getNumberOfLines() - 1;
					}
					return document.getLineOffset(bottom) + document.getLineLength(bottom);
				} catch (BadLocationException x) {
					// Do nothing
				}
			}
			return -1;
		}

		/**
		 * Return whether the given offset is in visible lines.
		 *
		 * @param offset the offset
		 * @return <code>true</code> if the given offset is in visible lines and <code>false</code>
		 *         otherwise.
		 */
		public boolean isInVisibleLines(int offset) {
			return offset >= startOffset && offset <= endOffset;
		}
	}

	private class MouseTracker implements MouseTrackListener, MouseMoveListener, MouseListener {

		private AbstractInlinedAnnotation fAnnotation;

		private Consumer<MouseEvent> fAction;

		private void update(MouseEvent e) {
			fAnnotation= null;
			fAction= null;
			AbstractInlinedAnnotation annotation= getInlinedAnnotationAtPoint(fViewer, new Point(e.x, e.y));
			if (annotation != null) {
				Consumer<MouseEvent> action= annotation.getAction(e);
				if (action != null) {
					fAnnotation= annotation;
					fAction= action;
				}
			}
		}

		@Override
		public void mouseHover(MouseEvent e) {
			update(e);
			if (fAnnotation != null) {
				fAnnotation.onMouseHover(e);
			}
		}

		@Override
		public void mouseMove(MouseEvent e) {
			if (fAnnotation != null) {
				AbstractInlinedAnnotation oldAnnotation= fAnnotation;
				update(e);
				if (!oldAnnotation.equals(fAnnotation)) {
					oldAnnotation.onMouseOut(e);
					fAnnotation= null;
					fAction= null;
				}
			}
		}

		@Override
		public void mouseDoubleClick(MouseEvent e) {
			// Do nothing
		}

		@Override
		public void mouseDown(MouseEvent e) {
			// Do nothing
		}

		@Override
		public void mouseUp(MouseEvent e) {
			if (e.button != 1) {
				return;
			}
			if (fAction != null) {
				fAction.accept(e);
			}
		}

		@Override
		public void mouseEnter(MouseEvent e) {
			// Do nothing
		}

		@Override
		public void mouseExit(MouseEvent e) {
			// Do nothing
		}

	}

	/**
	 * The source viewer
	 */
	private ISourceViewer fViewer;

	/**
	 * The annotation painter to use to draw the inlined annotations.
	 */
	private AnnotationPainter fPainter;

	/**
	 * Holds the current inlined annotations.
	 */
	private Set<AbstractInlinedAnnotation> fInlinedAnnotations;

	/**
	 * The mouse tracker used to support hover, click on inlined annotation.
	 */
	private final MouseTracker fMouseTracker= new MouseTracker();

	/**
	 * Install the inlined annotation support for the given viewer.
	 *
	 * @param viewer the source viewer
	 * @param painter the annotation painter to use to draw the inlined annotations.
	 */
	public void install(ISourceViewer viewer, AnnotationPainter painter) {
		Assert.isNotNull(viewer);
		Assert.isNotNull(painter);
		fViewer= viewer;
		fPainter= painter;
		initPainter();
		StyledText text= fViewer.getTextWidget();
		if (text == null || text.isDisposed()) {
			return;
		}
		if (fViewer instanceof ITextViewerExtension4) {
			updateStylesWidth= new UpdateStylesWidth();
			((ITextViewerExtension4) fViewer).addTextPresentationListener(updateStylesWidth);
		}
		visibleLines= new VisibleLines();
		fViewer.addViewportListener(visibleLines);
		text.addMouseListener(fMouseTracker);
		text.addMouseTrackListener(fMouseTracker);
		text.addMouseMoveListener(fMouseTracker);
		text.setLineSpacingProvider(this);
		setColor(text.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
	}

	/**
	 * Initialize painter with inlined drawing strategy.
	 */
	private void initPainter() {
		fPainter.addDrawingStrategy(INLINED_STRATEGY_ID, INLINED_STRATEGY);
		fPainter.addAnnotationType(AbstractInlinedAnnotation.TYPE, INLINED_STRATEGY_ID);
	}

	/**
	 * Set the color to use to draw the inlined annotations.
	 *
	 * @param color the color to use to draw the inlined annotations.
	 */
	public void setColor(Color color) {
		fPainter.setAnnotationTypeColor(AbstractInlinedAnnotation.TYPE, color);
	}

	/**
	 * Unisntall the inlined annotation support
	 */
	public void uninstall() {
		StyledText text= this.fViewer.getTextWidget();
		if (text != null && !text.isDisposed()) {
			text.removeMouseListener(this.fMouseTracker);
			text.removeMouseTrackListener(this.fMouseTracker);
			text.removeMouseMoveListener(this.fMouseTracker);
		}
		if (fViewer != null) {
			if (fViewer instanceof ITextViewerExtension4) {
				((ITextViewerExtension4) fViewer).removeTextPresentationListener(updateStylesWidth);
			}
			fViewer.removeViewportListener(visibleLines);
		}
		fViewer= null;
		fPainter= null;
	}

	/**
	 * Update the given inlined annotation.
	 *
	 * @param annotations the inlined annotation.
	 */
	public void updateAnnotations(Set<AbstractInlinedAnnotation> annotations) {
		IDocument document= fViewer != null ? fViewer.getDocument() : null;
		if (document == null) {
			// this case comes from when editor is closed before rendered is done.
			return;
		}
		IAnnotationModel annotationModel= fViewer.getAnnotationModel();
		if (annotationModel == null) {
			return;
		}
		Map<AbstractInlinedAnnotation, Position> annotationsToAdd= new HashMap<>();
		List<AbstractInlinedAnnotation> annotationsToRemove= fInlinedAnnotations != null
				? new ArrayList<>(fInlinedAnnotations)
				: Collections.emptyList();
		// Loop for annotations to update
		for (AbstractInlinedAnnotation ann : annotations) {
			if (!annotationsToRemove.remove(ann)) {
				// The annotation was not created, add it
				annotationsToAdd.put(ann, ann.getPosition());
			}
		}
		// Process annotations to remove
		for (AbstractInlinedAnnotation ann : annotationsToRemove) {
			// Mark annotation as deleted to ignore the draw
			ann.markDeleted(true);
		}
		// Update annotation model
		synchronized (getLockObject(annotationModel)) {
			// Update annotations with this inlined annotation support.
			for (AbstractInlinedAnnotation ann : annotations) {
				ann.setSupport(this);
			}
			if (annotationsToAdd.size() == 0 && annotationsToRemove.size() == 0) {
				// None change, do nothing. Here the user could change position of codemining
				// range
				// (ex: user key press
				// "Enter"), but we don't need to redraw the viewer because change of position
				// is done by AnnotationPainter.
			} else {
				if (annotationModel instanceof IAnnotationModelExtension) {
					((IAnnotationModelExtension) annotationModel).replaceAnnotations(
							annotationsToRemove.toArray(new Annotation[annotationsToRemove.size()]), annotationsToAdd);
				} else {
					removeInlinedAnnotations();
					Iterator<Entry<AbstractInlinedAnnotation, Position>> iter= annotationsToAdd.entrySet().iterator();
					while (iter.hasNext()) {
						Entry<AbstractInlinedAnnotation, Position> mapEntry= iter.next();
						annotationModel.addAnnotation(mapEntry.getKey(), mapEntry.getValue());
					}
				}
			}
			fInlinedAnnotations= annotations;
		}
	}

	/**
	 * Returns the existing codemining annotation with the given position information and null
	 * otherwise.
	 *
	 * @param pos the position
	 * @return the existing codemining annotation with the given position information and null
	 *         otherwise.
	 */
	@SuppressWarnings("unchecked")
	public <T extends AbstractInlinedAnnotation> T findExistingAnnotation(Position pos) {
		if (fInlinedAnnotations == null) {
			return null;
		}
		for (AbstractInlinedAnnotation ann : fInlinedAnnotations) {
			if (pos.equals(ann.getPosition()) && !ann.getPosition().isDeleted()) {
				try {
					return (T) ann;
				} catch (ClassCastException e) {
					// Do nothing
				}
			}
		}
		return null;
	}

	/**
	 * Returns the lock object for the given annotation model.
	 *
	 * @param annotationModel the annotation model
	 * @return the annotation model's lock object
	 */
	private Object getLockObject(IAnnotationModel annotationModel) {
		if (annotationModel instanceof ISynchronizable) {
			Object lock= ((ISynchronizable) annotationModel).getLockObject();
			if (lock != null)
				return lock;
		}
		return annotationModel;
	}

	/**
	 * Remove the inlined annotations.
	 */
	private void removeInlinedAnnotations() {

		IAnnotationModel annotationModel= fViewer.getAnnotationModel();
		if (annotationModel == null || fInlinedAnnotations == null)
			return;

		synchronized (getLockObject(annotationModel)) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel).replaceAnnotations(
						fInlinedAnnotations.toArray(new Annotation[fInlinedAnnotations.size()]), null);
			} else {
				for (AbstractInlinedAnnotation annotation : fInlinedAnnotations)
					annotationModel.removeAnnotation(annotation);
			}
			fInlinedAnnotations= null;
		}
	}

	/**
	 * Returns the line spacing from the given line index with the codemining annotations height and
	 * null otherwise.
	 */
	@SuppressWarnings("boxing")
	@Override
	public Integer getLineSpacing(int lineIndex) {
		AbstractInlinedAnnotation annotation= getInlinedAnnotationAtLine(fViewer, lineIndex);
		return (annotation instanceof LineHeaderAnnotation)
				? ((LineHeaderAnnotation) annotation).getHeight()
				: null;
	}

	/**
	 * Returns the {@link AbstractInlinedAnnotation} from the given line index and null otherwise.
	 *
	 * @param viewer the source viewer
	 * @param lineIndex the line index.
	 * @return the {@link AbstractInlinedAnnotation} from the given line index and null otherwise.
	 */
	private static AbstractInlinedAnnotation getInlinedAnnotationAtLine(ISourceViewer viewer, int lineIndex) {
		if (viewer == null) {
			return null;
		}
		IAnnotationModel annotationModel= viewer.getAnnotationModel();
		if (annotationModel == null) {
			return null;
		}
		IDocument document= viewer.getDocument();
		int lineNumber= lineIndex + 1;
		if (lineNumber > document.getNumberOfLines()) {
			return null;
		}
		try {
			if (viewer instanceof ITextViewerExtension5) {
				lineNumber= ((ITextViewerExtension5) viewer).widgetLine2ModelLine(lineNumber);
			}
			IRegion line= document.getLineInformation(lineNumber);
			return getInlinedAnnotationAtOffset(viewer, line.getOffset(), line.getLength());
		} catch (BadLocationException e) {
			return null;
		}
	}

	/**
	 * Returns the {@link AbstractInlinedAnnotation} from the given point and null otherwise.
	 *
	 * @param viewer the source viewer
	 * @param point the origin of character bounding box relative to the origin of the widget client
	 *            area.
	 * @return the {@link AbstractInlinedAnnotation} from the given point and null otherwise.
	 */
	private static AbstractInlinedAnnotation getInlinedAnnotationAtPoint(ISourceViewer viewer, Point point) {
		AbstractInlinedAnnotation annotation= getLineContentAnnotationAtPoint(viewer, point);
		if (annotation != null) {
			return annotation;
		}
		return getLineHeaderAnnotationAtPoint(viewer, point);
	}

	/**
	 * Returns the {@link AbstractInlinedAnnotation} line content from the given point and null
	 * otherwise.
	 *
	 * @param viewer the source viewer
	 * @param point the origin of character bounding box relative to the origin of the widget client
	 *            area.
	 * @return the {@link AbstractInlinedAnnotation} line content from the given point and null
	 *         otherwise.
	 */
	private static AbstractInlinedAnnotation getLineContentAnnotationAtPoint(ISourceViewer viewer, Point point) {
		StyledText styledText= viewer.getTextWidget();
		int offset= styledText.getOffsetAtPoint(point);
		if (offset == -1) {
			return null;
		}
		if (viewer instanceof ITextViewerExtension5) {
			offset= ((ITextViewerExtension5) viewer).widgetOffset2ModelOffset(offset);
		}
		AbstractInlinedAnnotation annotation= getInlinedAnnotationAtOffset(viewer, offset, 1);
		if (annotation instanceof LineContentAnnotation) {
			return annotation;
		}
		return null;
	}

	/**
	 * Returns the {@link AbstractInlinedAnnotation} line header from the given point and null
	 * otherwise.
	 *
	 * @param viewer the source viewer
	 * @param point the origin of character bounding box relative to the origin of the widget client
	 *            area.
	 * @return the {@link AbstractInlinedAnnotation} line header from the given point and null
	 *         otherwise.
	 */
	private static AbstractInlinedAnnotation getLineHeaderAnnotationAtPoint(ISourceViewer viewer, Point point) {
		StyledText styledText= viewer.getTextWidget();
		int lineIndex= styledText.getLineIndex(point.y);
		AbstractInlinedAnnotation annotation= getInlinedAnnotationAtLine(viewer, lineIndex);
		if (annotation instanceof LineHeaderAnnotation) {
			return annotation;
		}
		return null;
	}

	/**
	 * Returns the {@link AbstractInlinedAnnotation} from the given offset and null otherwise.
	 *
	 * @param viewer the source viewer
	 * @param offset the start position of the region, must be >= 0
	 * @param length the length of the region, must be >= 0
	 * @return the {@link AbstractInlinedAnnotation} from the given offset and null otherwise.
	 */
	private static AbstractInlinedAnnotation getInlinedAnnotationAtOffset(ISourceViewer viewer, int offset, int length) {
		if (viewer == null) {
			return null;
		}
		IAnnotationModel annotationModel= viewer.getAnnotationModel();
		if (annotationModel == null) {
			return null;
		}
		Iterator<Annotation> iter= (annotationModel instanceof IAnnotationModelExtension2)
				? ((IAnnotationModelExtension2) annotationModel).getAnnotationIterator(offset,
						length, true, true)
				: annotationModel.getAnnotationIterator();
		while (iter.hasNext()) {
			Annotation ann= iter.next();
			if (ann instanceof AbstractInlinedAnnotation) {
				Position p= annotationModel.getPosition(ann);
				if (p != null) {
					if (p.overlapsWith(offset, length)) {
						return (AbstractInlinedAnnotation) ann;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Execute UI {@link StyledText} function which requires UI Thread.
	 *
	 * @param text the styled text
	 * @param fn the function to execute.
	 */
	static void runInUIThread(StyledText text, Consumer<StyledText> fn) {
		if (text == null || text.isDisposed()) {
			return;
		}
		Display display= text.getDisplay();
		if (display.getThread() == Thread.currentThread()) {
			try {
				fn.accept(text);
			} catch (Exception e) {
				// Ignore UI error
			}
		} else {
			display.asyncExec(() -> {
				if (text.isDisposed()) {
					return;
				}
				try {
					fn.accept(text);
				} catch (Exception e) {
					// Ignore UI error
				}
			});
		}
	}

	/**
	 * Return whether the given offset is in visible lines.
	 *
	 * @param offset the offset
	 * @return <code>true</code> if the given offset is in visible lines and <code>false</code>
	 *         otherwise.
	 */
	boolean isInVisibleLines(int offset) {
		return visibleLines.isInVisibleLines(offset);
	}
}